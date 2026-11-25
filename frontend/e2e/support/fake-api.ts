import { BrowserContext, Route } from '@playwright/test';
import {
  expectRequestToMatchContract,
  expectResponseToMatchContract,
} from '../../src/testing/openapi-contract';
import { APP_ORIGIN } from './fake-idp';

/**
 * API falsa com estado, no lugar do backend nos testes e2e. Imita os contratos e os efeitos que a
 * interface precisa mostrar: modelo de leitura atrasado (404 logo após criar), saga de estoque
 * (CREATED → CONFIRMED, ou CANCELLED sem estoque), idempotência na criação e If-Match na reposição.
 */
export const API_ORIGIN = 'http://localhost:8080';

type Status = 'CREATED' | 'STOCK_RESERVED' | 'CONFIRMED' | 'SHIPPED' | 'CANCELLED';

interface Product {
  sku: string;
  name: string;
  currency: string;
  unitPrice: number;
  available: number;
  reserved: number;
  version: number;
}

interface StoredOrder {
  customerId: string;
  /** Pedidos semeados já estão projetados e não passam pela saga. */
  seeded: boolean;
  reads: number;
  order: {
    id: string;
    status: Status;
    currency: string;
    total: number;
    items: { sku: string; quantity: number; unitPrice: number; total: number }[];
    cancellationReason: string | null;
    createdAt: string;
    updatedAt: string;
  };
}

const PROBLEM_BASE = 'https://github.com/caiodassumpcao/orderflow/blob/main/docs/api/problems.md#';

export class FakeApi {
  readonly products: Product[] = [
    {
      sku: 'SKU-001',
      name: 'Caneca de cerâmica',
      currency: 'BRL',
      unitPrice: 19.9,
      available: 500,
      reserved: 0,
      version: 0,
    },
    {
      sku: 'SKU-002',
      name: 'Fone de ouvido Bluetooth',
      currency: 'BRL',
      unitPrice: 100,
      available: 200,
      reserved: 0,
      version: 0,
    },
    {
      sku: 'SKU-005',
      name: 'Kit de adesivos',
      currency: 'BRL',
      unitPrice: 9.9,
      available: 0,
      reserved: 0,
      version: 4,
    },
  ];
  readonly orders: StoredOrder[] = [];
  /** Respostas servidas ou corpos recebidos que fogem de docs/openapi.yaml (conferido após cada teste). */
  readonly contractViolations: string[] = [];
  private readonly keys = new Map<string, string>();
  private sequence = 0;

  /** Pedido já existente (projetado), para as telas de listagem e de operação. */
  seedOrder(customerId: string, status: Status, sku = 'SKU-001', quantity = 1): string {
    const product = this.product(sku);
    const id = this.newId();
    const total = Math.round(product.unitPrice * quantity * 100) / 100;
    const at = new Date(Date.now() - 3_600_000 * (this.orders.length + 1)).toISOString();
    this.orders.push({
      customerId,
      seeded: true,
      reads: 99,
      order: {
        id,
        status,
        currency: product.currency,
        total,
        items: [{ sku, quantity, unitPrice: product.unitPrice, total }],
        cancellationReason: null,
        createdAt: at,
        updatedAt: at,
      },
    });
    return id;
  }

  async install(context: BrowserContext): Promise<void> {
    await context.route(
      (url) => url.origin === API_ORIGIN,
      (route) => this.handle(route),
    );
  }

  private async handle(route: Route): Promise<void> {
    const request = route.request();
    if (request.method() === 'OPTIONS') {
      return route.fulfill({ status: 204, headers: this.cors() });
    }
    const url = new URL(request.url());
    const path = url.pathname;
    const subject = this.subject(request.headers()['authorization']);
    if (!subject) {
      return this.problem(route, 401, 'about:blank');
    }
    const body = request.postDataJSON?.() ?? null;
    if (body !== null) {
      this.verify(() => expectRequestToMatchContract(request.method(), path, body));
    }
    const method = request.method();
    let match: RegExpMatchArray | null;

    if (method === 'GET' && path === '/api/v1/catalog') {
      return this.json(route, 200, {
        items: this.products.map(({ sku, name, currency, unitPrice, available }) => ({
          sku,
          name,
          currency,
          unitPrice,
          inStock: available > 0,
        })),
        nextCursor: null,
      });
    }
    if (method === 'GET' && path === '/api/v1/orders') {
      return this.json(
        route,
        200,
        this.page(
          this.orders.filter((o) => o.customerId === subject),
          url,
        ),
      );
    }
    if (method === 'POST' && path === '/api/v1/orders') {
      return this.place(route, subject, request.headers()['idempotency-key'], body);
    }
    if ((match = path.match(/^\/api\/v1\/orders\/([^/]+)$/)) && method === 'GET') {
      return this.readOrder(route, match[1], subject);
    }
    if ((match = path.match(/^\/api\/v1\/orders\/([^/]+)\/cancel$/)) && method === 'POST') {
      return this.transition(
        route,
        match[1],
        subject,
        ['CREATED'],
        'CANCELLED',
        body?.reason ?? null,
      );
    }
    if ((match = path.match(/^\/api\/v1\/orders\/([^/]+)\/ship$/)) && method === 'POST') {
      return this.transition(route, match[1], null, ['CONFIRMED'], 'SHIPPED', null);
    }
    if (method === 'GET' && path === '/api/v1/admin/orders') {
      const status = url.searchParams.get('status');
      const items = this.orders.filter((o) => !status || o.order.status === status);
      return this.json(route, 200, {
        items: this.page(items, url).items.map((summary, i) => ({
          ...summary,
          customerId: items[i].customerId,
        })),
        nextCursor: null,
      });
    }
    if ((match = path.match(/^\/api\/v1\/admin\/orders\/([^/]+)$/)) && method === 'GET') {
      const stored = this.orders.find((o) => o.order.id === match?.[1]);
      return stored
        ? this.json(route, 200, { customerId: stored.customerId, order: stored.order })
        : this.problem(route, 404, 'order-not-found');
    }
    if (method === 'GET' && path === '/api/v1/inventory') {
      return this.json(route, 200, { items: this.products, nextCursor: null });
    }
    if (method === 'POST' && path === '/api/v1/inventory') {
      if (this.products.some((p) => p.sku === body.sku)) {
        return this.problem(route, 409, 'stock-item-already-exists');
      }
      const product: Product = {
        ...body,
        available: body.initialQuantity,
        reserved: 0,
        version: 0,
      };
      delete (product as Partial<{ initialQuantity: number }>).initialQuantity;
      this.products.push(product);
      this.products.sort((a, b) => a.sku.localeCompare(b.sku));
      return this.json(route, 201, product, { ETag: '"0"' });
    }
    if ((match = path.match(/^\/api\/v1\/inventory\/([^/]+)$/)) && method === 'GET') {
      const product = this.products.find((p) => p.sku === match?.[1]);
      return product
        ? this.json(route, 200, product)
        : this.problem(route, 404, 'stock-item-not-found');
    }
    if ((match = path.match(/^\/api\/v1\/inventory\/([^/]+)\/restock$/)) && method === 'POST') {
      const product = this.product(match[1]);
      if (request.headers()['if-match'] !== `"${product.version}"`) {
        return this.problem(route, 412, 'stock-item-modified');
      }
      product.available += body.quantity;
      product.version++;
      return this.json(route, 200, product, { ETag: `"${product.version}"` });
    }
    if (method === 'GET' && path === '/api/v1/dashboard/summary') {
      return this.json(route, 200, this.dashboard(url));
    }
    return this.problem(route, 404, 'about:blank');
  }

  private place(
    route: Route,
    subject: string,
    key: string | undefined,
    body: { currency: string; items: { sku: string; quantity: number; unitPrice: number }[] },
  ) {
    const replay = key ? this.keys.get(`${subject}:${key}`) : undefined;
    const existing = this.orders.find((o) => o.order.id === replay);
    if (existing) {
      return this.json(route, 201, existing.order, { 'Idempotency-Replayed': 'true' });
    }
    const id = this.newId();
    const items = body.items.map((item) => ({
      ...item,
      total: Math.round(item.unitPrice * item.quantity * 100) / 100,
    }));
    const now = new Date().toISOString();
    const stored: StoredOrder = {
      customerId: subject,
      seeded: false,
      reads: 0,
      order: {
        id,
        status: 'CREATED',
        currency: body.currency,
        total: items.reduce((sum, item) => sum + item.total, 0),
        items,
        cancellationReason: null,
        createdAt: now,
        updatedAt: now,
      },
    };
    this.orders.unshift(stored);
    if (key) {
      this.keys.set(`${subject}:${key}`, id);
    }
    return this.json(route, 201, stored.order, { Location: `/api/v1/orders/${id}` });
  }

  /** Primeira leitura: 404 (projeção atrasada). Segunda: CREATED. Depois: decisão da saga. */
  private readOrder(route: Route, id: string, subject: string) {
    const stored = this.orders.find((o) => o.order.id === id && o.customerId === subject);
    if (!stored) {
      return this.problem(route, 404, 'order-not-found');
    }
    if (stored.seeded) {
      return this.json(route, 200, stored.order);
    }
    stored.reads++;
    if (stored.reads === 1) {
      return this.problem(route, 404, 'order-not-found');
    }
    if (stored.reads >= 3 && stored.order.status === 'CREATED') {
      const missing = stored.order.items.find(
        (item) => this.product(item.sku).available < item.quantity,
      );
      stored.order.status = missing ? 'CANCELLED' : 'CONFIRMED';
      stored.order.cancellationReason = missing
        ? `Stock rejected: OUT_OF_STOCK (${missing.sku})`
        : null;
      stored.order.updatedAt = new Date().toISOString();
    }
    return this.json(route, 200, stored.order);
  }

  private transition(
    route: Route,
    id: string,
    subject: string | null,
    from: Status[],
    to: Status,
    reason: string | null,
  ) {
    const stored = this.orders.find(
      (o) => o.order.id === id && (!subject || o.customerId === subject),
    );
    if (!stored) {
      return this.problem(route, 404, 'order-not-found');
    }
    if (!from.includes(stored.order.status)) {
      return this.problem(route, 409, 'order-state-conflict', {
        currentStatus: stored.order.status,
      });
    }
    stored.order.status = to;
    stored.order.cancellationReason = to === 'CANCELLED' ? reason : stored.order.cancellationReason;
    stored.order.updatedAt = new Date().toISOString();
    return this.json(route, 200, stored.order);
  }

  private page(orders: StoredOrder[], url: URL) {
    const status = url.searchParams.get('status');
    return {
      items: orders
        .filter((o) => !status || o.order.status === status)
        .map(({ order }) => ({
          id: order.id,
          status: order.status,
          currency: order.currency,
          total: order.total,
          itemCount: order.items.length,
          createdAt: order.createdAt,
          updatedAt: order.updatedAt,
        })),
      nextCursor: null,
    };
  }

  private dashboard(url: URL) {
    const byStatus: Record<Status, number> = {
      CREATED: 0,
      STOCK_RESERVED: 0,
      CONFIRMED: 0,
      SHIPPED: 0,
      CANCELLED: 0,
    };
    let revenue = 0;
    for (const { order } of this.orders) {
      byStatus[order.status]++;
      if (order.status === 'CONFIRMED' || order.status === 'SHIPPED') {
        revenue += order.total;
      }
    }
    const days = Number(url.searchParams.get('days') ?? 7);
    const today = new Date();
    return {
      generatedAt: today.toISOString(),
      timeZone: url.searchParams.get('timeZone') ?? 'UTC',
      totalOrders: this.orders.length,
      ordersByStatus: byStatus,
      confirmedRevenue: revenue
        ? [{ currency: 'BRL', amount: Math.round(revenue * 100) / 100 }]
        : [],
      daily: Array.from({ length: days }, (_, i) => {
        const date = new Date(today.getTime() - (days - 1 - i) * 86_400_000)
          .toISOString()
          .slice(0, 10);
        return { date, orders: i === days - 1 ? this.orders.length : i % 3, cancelled: 0 };
      }),
    };
  }

  private product(sku: string): Product {
    const product = this.products.find((p) => p.sku === sku);
    if (!product) {
      throw new Error(`Unknown product ${sku}`);
    }
    return product;
  }

  private newId(): string {
    this.sequence++;
    return `0193e2e0-0000-7000-8000-${String(this.sequence).padStart(12, '0')}`;
  }

  /** O `sub` do access token; a assinatura já foi conferida pela SPA ao receber o token. */
  private subject(authorization: string | undefined): string | null {
    const payload = authorization?.match(/^Bearer [^.]+\.([^.]+)\./)?.[1];
    if (!payload) {
      return null;
    }
    return JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')).sub ?? null;
  }

  private cors(extra: Record<string, string> = {}) {
    return {
      'Access-Control-Allow-Origin': APP_ORIGIN,
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Authorization, Content-Type, Idempotency-Key, If-Match',
      'Access-Control-Expose-Headers': 'ETag, Location, Retry-After, Idempotency-Replayed',
      ...extra,
    };
  }

  private json(route: Route, status: number, body: unknown, headers: Record<string, string> = {}) {
    const request = route.request();
    this.verify(() =>
      expectResponseToMatchContract(
        request.method(),
        new URL(request.url()).pathname,
        status,
        body,
      ),
    );
    return route.fulfill({ status, json: body, headers: this.cors(headers) });
  }

  private verify(check: () => void): void {
    try {
      check();
    } catch (error) {
      this.contractViolations.push((error as Error).message);
    }
  }

  private problem(route: Route, status: number, type: string, extra: Record<string, unknown> = {}) {
    return route.fulfill({
      status,
      contentType: 'application/problem+json',
      headers: this.cors(),
      body: JSON.stringify({
        type: type === 'about:blank' ? type : PROBLEM_BASE + type,
        title: type,
        status,
        correlationId: 'e2e-correlation',
        ...extra,
      }),
    });
  }
}
