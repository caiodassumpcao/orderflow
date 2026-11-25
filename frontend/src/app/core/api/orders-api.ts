import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import {
  AdminOrder,
  AdminOrderSummary,
  Order,
  OrderStatus,
  OrderSummary,
  Page,
  PlaceOrder,
} from './models';

export interface OrderListParams {
  readonly status?: OrderStatus | null;
  readonly cursor?: string | null;
  readonly limit?: number;
}

export interface AdminOrderListParams extends OrderListParams {
  readonly customerId?: string | null;
}

function params(values: Record<string, string | number | null | undefined>): HttpParams {
  let result = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== null && value !== undefined && value !== '') {
      result = result.set(key, String(value));
    }
  }
  return result;
}

/** Pedidos: comandos (modelo de escrita) e consultas (modelo de leitura). */
@Injectable({ providedIn: 'root' })
export class OrdersApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiUrl}/api/v1`;

  list(query: OrderListParams = {}): Observable<Page<OrderSummary>> {
    return this.http.get<Page<OrderSummary>>(`${this.base}/orders`, {
      params: params({ status: query.status, cursor: query.cursor, limit: query.limit }),
    });
  }

  get(id: string): Observable<Order> {
    return this.http.get<Order>(`${this.base}/orders/${encodeURIComponent(id)}`);
  }

  /** A mesma chave repete o pedido original em vez de criar outro (idempotência). */
  place(order: PlaceOrder, idempotencyKey: string): Observable<Order> {
    return this.http.post<Order>(`${this.base}/orders`, order, {
      headers: { 'Idempotency-Key': idempotencyKey },
    });
  }

  cancel(id: string, reason?: string): Observable<Order> {
    const body = reason?.trim() ? { reason: reason.trim() } : {};
    return this.http.post<Order>(`${this.base}/orders/${encodeURIComponent(id)}/cancel`, body);
  }

  ship(id: string): Observable<Order> {
    return this.http.post<Order>(`${this.base}/orders/${encodeURIComponent(id)}/ship`, null);
  }

  listAll(query: AdminOrderListParams = {}): Observable<Page<AdminOrderSummary>> {
    return this.http.get<Page<AdminOrderSummary>>(`${this.base}/admin/orders`, {
      params: params({
        status: query.status,
        customerId: query.customerId?.trim(),
        cursor: query.cursor,
        limit: query.limit,
      }),
    });
  }

  getAny(id: string): Observable<AdminOrder> {
    return this.http.get<AdminOrder>(`${this.base}/admin/orders/${encodeURIComponent(id)}`);
  }
}
