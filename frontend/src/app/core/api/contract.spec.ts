import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import {
  expectRequestToMatchContract,
  expectResponseToMatchContract,
} from '../../../testing/openapi-contract';
import {
  API,
  ORDER_ID,
  aCatalogItem,
  aDashboard,
  aStockItem,
  aSummary,
  anAdminSummary,
  anOrder,
  testProviders,
} from '../../../testing/test-support';
import { InventoryApi } from './inventory-api';
import { OrdersApi } from './orders-api';

/**
 * Teste de contrato do consumidor: o que a SPA assume das respostas (as fábricas usadas em todos
 * os testes) e o que ela envia têm de bater com docs/openapi.yaml, gerado pelo backend.
 */
describe('API contract (docs/openapi.yaml)', () => {
  describe('responses the SPA relies on', () => {
    it.each([
      ['GET', '/api/v1/orders', 200, { items: [aSummary()], nextCursor: 'opaque' }],
      ['GET', '/api/v1/orders', 200, { items: [], nextCursor: null }],
      ['GET', `/api/v1/orders/${ORDER_ID}`, 200, anOrder()],
      [
        'GET',
        `/api/v1/orders/${ORDER_ID}`,
        200,
        anOrder({
          status: 'CANCELLED',
          cancellationReason: 'Stock rejected: OUT_OF_STOCK (SKU-1)',
        }),
      ],
      ['POST', '/api/v1/orders', 201, anOrder()],
      ['POST', `/api/v1/orders/${ORDER_ID}/cancel`, 200, anOrder({ status: 'CANCELLED' })],
      ['POST', `/api/v1/orders/${ORDER_ID}/ship`, 200, anOrder({ status: 'SHIPPED' })],
      ['GET', '/api/v1/admin/orders', 200, { items: [anAdminSummary()], nextCursor: null }],
      ['GET', `/api/v1/admin/orders/${ORDER_ID}`, 200, { customerId: 'c-1', order: anOrder() }],
      ['GET', '/api/v1/catalog', 200, { items: [aCatalogItem()], nextCursor: 'SKU-001' }],
      ['GET', '/api/v1/inventory', 200, { items: [aStockItem()], nextCursor: null }],
      ['GET', '/api/v1/inventory/SKU-001', 200, aStockItem()],
      ['POST', '/api/v1/inventory', 201, aStockItem()],
      ['POST', '/api/v1/inventory/SKU-001/restock', 200, aStockItem()],
      ['GET', '/api/v1/dashboard/summary', 200, aDashboard()],
    ])('%s %s %i', (method, path, status, body) => {
      expect(() => expectResponseToMatchContract(method, path, status, body)).not.toThrow();
    });

    it('catches a field the API does not send or a wrong type', () => {
      expect(() =>
        expectResponseToMatchContract('GET', `/api/v1/orders/${ORDER_ID}`, 200, {
          ...anOrder(),
          totalAmount: 1,
        }),
      ).toThrow(/additional properties/);
      expect(() =>
        expectResponseToMatchContract('GET', '/api/v1/catalog', 200, {
          items: [{ ...aCatalogItem(), inStock: 'yes' }],
          nextCursor: null,
        }),
      ).toThrow(/boolean/);
      expect(() =>
        expectResponseToMatchContract('GET', `/api/v1/orders/${ORDER_ID}`, 200, {
          ...anOrder(),
          status: 'PAID',
        }),
      ).toThrow(/allowed values/);
    });

    it('rejects endpoints and statuses the contract does not have', () => {
      expect(() => expectResponseToMatchContract('GET', '/api/v1/unknown', 200, {})).toThrow(
        /não existe no contrato/,
      );
      expect(() => expectResponseToMatchContract('POST', '/api/v1/orders', 200, anOrder())).toThrow(
        /status não documentado/,
      );
    });
  });

  describe('requests the SPA sends', () => {
    let http: HttpTestingController;

    beforeEach(() => {
      TestBed.configureTestingModule({ providers: testProviders() });
      http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    function sentBody(method: string, url: string): unknown {
      const request = http.expectOne((r) => r.method === method && r.url === `${API}${url}`);
      request.flush({});
      return request.request.body;
    }

    it('places, cancels and ships orders with accepted bodies', () => {
      const orders = TestBed.inject(OrdersApi);
      orders
        .place(
          { currency: 'BRL', items: [{ sku: 'SKU-001', quantity: 2, unitPrice: 19.9 }] },
          'k-12345678',
        )
        .subscribe();
      orders.cancel(ORDER_ID, 'mudei de ideia').subscribe();
      orders.ship(ORDER_ID).subscribe();

      expectRequestToMatchContract('POST', '/api/v1/orders', sentBody('POST', '/api/v1/orders'));
      expectRequestToMatchContract(
        'POST',
        `/api/v1/orders/${ORDER_ID}/cancel`,
        sentBody('POST', `/api/v1/orders/${ORDER_ID}/cancel`),
      );
      // O envio não tem corpo no contrato; a SPA manda null.
      expect(sentBody('POST', `/api/v1/orders/${ORDER_ID}/ship`)).toBeNull();
    });

    it('registers and restocks products with accepted bodies', () => {
      const inventory = TestBed.inject(InventoryApi);
      inventory
        .register({
          sku: 'SKU-NEW',
          name: 'Garrafa',
          currency: 'BRL',
          unitPrice: 89.9,
          initialQuantity: 0,
        })
        .subscribe();
      inventory.restock(aStockItem(), 5).subscribe();

      expectRequestToMatchContract(
        'POST',
        '/api/v1/inventory',
        sentBody('POST', '/api/v1/inventory'),
      );
      expectRequestToMatchContract(
        'POST',
        '/api/v1/inventory/SKU-001/restock',
        sentBody('POST', '/api/v1/inventory/SKU-001/restock'),
      );
    });

    it('catches a body the API would refuse', () => {
      expect(() =>
        expectRequestToMatchContract('POST', '/api/v1/orders', { currency: 'real', items: [] }),
      ).toThrow(/pattern|fewer than 1/);
    });
  });
});
