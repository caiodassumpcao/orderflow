import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { firstValueFrom } from 'rxjs';
import {
  API,
  aCatalogItem,
  aDashboard,
  aStockItem,
  anOrder,
  testProviders,
} from '../../../testing/test-support';
import { DashboardApi } from './dashboard-api';
import { InventoryApi, MAX_CATALOG_PAGES } from './inventory-api';
import { OrdersApi } from './orders-api';

describe('API clients', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('OrdersApi', () => {
    let api: OrdersApi;

    beforeEach(() => (api = TestBed.inject(OrdersApi)));

    it('lists orders sending only the filters that are set', () => {
      api.list({ status: 'CANCELLED', cursor: null, limit: 20 }).subscribe();

      const request = http.expectOne((r) => r.url === `${API}/api/v1/orders`);
      expect(request.request.params.keys()).toEqual(['status', 'limit']);
      request.flush({ items: [], nextCursor: null });
    });

    it('places an order with the idempotency key', () => {
      api.place({ currency: 'BRL', items: [] }, 'key-123').subscribe();

      const request = http.expectOne(`${API}/api/v1/orders`);
      expect(request.request.method).toBe('POST');
      expect(request.request.headers.get('Idempotency-Key')).toBe('key-123');
      request.flush(anOrder());
    });

    it('cancels with a trimmed reason, or without a body field when there is none', () => {
      api.cancel('a/b', '  mudei de ideia ').subscribe();
      api.cancel('id-2', '   ').subscribe();

      const withReason = http.expectOne(`${API}/api/v1/orders/a%2Fb/cancel`);
      expect(withReason.request.body).toEqual({ reason: 'mudei de ideia' });
      withReason.flush(anOrder());
      const withoutReason = http.expectOne(`${API}/api/v1/orders/id-2/cancel`);
      expect(withoutReason.request.body).toEqual({});
      withoutReason.flush(anOrder());
    });

    it('reads, ships and lists orders as an admin', () => {
      api.get('id-1').subscribe();
      api.ship('id-1').subscribe();
      api.getAny('id-1').subscribe();
      api.listAll({ customerId: '  alice ', status: null }).subscribe();

      http.expectOne(`${API}/api/v1/orders/id-1`).flush(anOrder());
      http.expectOne(`${API}/api/v1/orders/id-1/ship`).flush(anOrder());
      http
        .expectOne(`${API}/api/v1/admin/orders/id-1`)
        .flush({ customerId: 'c', order: anOrder() });
      const list = http.expectOne((r) => r.url === `${API}/api/v1/admin/orders`);
      expect(list.request.params.get('customerId')).toBe('alice');
      expect(list.request.params.has('status')).toBe(false);
      list.flush({ items: [], nextCursor: null });
    });
  });

  describe('InventoryApi', () => {
    let api: InventoryApi;

    beforeEach(() => (api = TestBed.inject(InventoryApi)));

    it('joins catalog pages until there is no next cursor', async () => {
      const result = firstValueFrom(api.catalog());

      const first = http.expectOne(
        (r) => r.url === `${API}/api/v1/catalog` && !r.params.has('cursor'),
      );
      expect(first.request.params.get('limit')).toBe('100');
      first.flush({ items: [aCatalogItem({ sku: 'SKU-001' })], nextCursor: 'SKU-001' });
      http
        .expectOne((r) => r.params.get('cursor') === 'SKU-001')
        .flush({ items: [aCatalogItem({ sku: 'SKU-002' })], nextCursor: null });

      expect((await result).map((item) => item.sku)).toEqual(['SKU-001', 'SKU-002']);
    });

    it('stops after the maximum number of catalog pages', async () => {
      const result = firstValueFrom(api.catalog());

      for (let page = 1; page <= MAX_CATALOG_PAGES; page++) {
        http
          .expectOne((r) => r.url === `${API}/api/v1/catalog`)
          .flush({ items: [aCatalogItem({ sku: `SKU-00${page}` })], nextCursor: `SKU-00${page}` });
      }

      expect(await result).toHaveLength(MAX_CATALOG_PAGES);
    });

    it('restocks with the version seen in If-Match', () => {
      api.restock(aStockItem({ sku: 'SKU-001', version: 7 }), 5).subscribe();

      const request = http.expectOne(`${API}/api/v1/inventory/SKU-001/restock`);
      expect(request.request.headers.get('If-Match')).toBe('"7"');
      expect(request.request.body).toEqual({ quantity: 5 });
      request.flush(aStockItem());
    });

    it('lists, reads and registers products', () => {
      api.list('SKU-009').subscribe();
      api.get('SKU-001').subscribe();
      api
        .register({
          sku: 'SKU-NEW',
          name: 'Novo',
          currency: 'BRL',
          unitPrice: 1,
          initialQuantity: 0,
        })
        .subscribe();

      const list = http.expectOne((r) => r.url === `${API}/api/v1/inventory` && r.method === 'GET');
      expect(list.request.params.get('cursor')).toBe('SKU-009');
      list.flush({ items: [], nextCursor: null });
      http.expectOne(`${API}/api/v1/inventory/SKU-001`).flush(aStockItem());
      http
        .expectOne((r) => r.url === `${API}/api/v1/inventory` && r.method === 'POST')
        .flush(aStockItem());
    });
  });

  it('asks the dashboard for the period in the given time zone', () => {
    TestBed.inject(DashboardApi).summary(7, 'America/Sao_Paulo').subscribe();

    const request = http.expectOne((r) => r.url === `${API}/api/v1/dashboard/summary`);
    expect(request.request.params.get('days')).toBe('7');
    expect(request.request.params.get('timeZone')).toBe('America/Sao_Paulo');
    request.flush(aDashboard());
  });
});
