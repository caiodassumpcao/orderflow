/** Contratos da API (ver a tabela de endpoints no README e o Swagger em dev). */

export type OrderStatus = 'CREATED' | 'STOCK_RESERVED' | 'CONFIRMED' | 'SHIPPED' | 'CANCELLED';

export const ORDER_STATUSES: readonly OrderStatus[] = [
  'CREATED',
  'STOCK_RESERVED',
  'CONFIRMED',
  'SHIPPED',
  'CANCELLED',
];

export interface Page<T> {
  readonly items: readonly T[];
  /** Valor para o parâmetro `cursor` da próxima página; nulo na última. */
  readonly nextCursor: string | null;
}

export interface OrderItem {
  readonly sku: string;
  readonly quantity: number;
  readonly unitPrice: number;
  readonly total: number;
}

export interface Order {
  readonly id: string;
  readonly status: OrderStatus;
  readonly currency: string;
  readonly total: number;
  readonly items: readonly OrderItem[];
  readonly cancellationReason: string | null;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface OrderSummary {
  readonly id: string;
  readonly status: OrderStatus;
  readonly currency: string;
  readonly total: number;
  readonly itemCount: number;
  readonly createdAt: string;
  readonly updatedAt: string;
}

export interface AdminOrderSummary extends OrderSummary {
  readonly customerId: string;
}

export interface AdminOrder {
  readonly customerId: string;
  readonly order: Order;
}

export interface PlaceOrder {
  readonly currency: string;
  readonly items: readonly {
    readonly sku: string;
    readonly quantity: number;
    readonly unitPrice: number;
  }[];
}

export interface CatalogItem {
  readonly sku: string;
  readonly name: string;
  readonly currency: string;
  readonly unitPrice: number;
  readonly inStock: boolean;
}

export interface StockItem {
  readonly sku: string;
  readonly name: string;
  readonly currency: string;
  readonly unitPrice: number;
  readonly available: number;
  readonly reserved: number;
  /** Versão atual; vai no `If-Match` da reposição. */
  readonly version: number;
}

export interface RegisterProduct {
  readonly sku: string;
  readonly name: string;
  readonly currency: string;
  readonly unitPrice: number;
  readonly initialQuantity: number;
}

export interface DashboardSummary {
  readonly generatedAt: string;
  readonly timeZone: string;
  readonly totalOrders: number;
  readonly ordersByStatus: Readonly<Record<OrderStatus, number>>;
  readonly confirmedRevenue: readonly { readonly currency: string; readonly amount: number }[];
  readonly daily: readonly {
    readonly date: string;
    readonly orders: number;
    readonly cancelled: number;
  }[];
}
