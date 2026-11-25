import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { EMPTY, Observable, expand, reduce } from 'rxjs';
import { APP_CONFIG } from '../config/app-config';
import { CatalogItem, Page, RegisterProduct, StockItem } from './models';

/** Teto de páginas lidas de uma vez no catálogo da criação de pedidos (100 produtos cada). */
export const MAX_CATALOG_PAGES = 5;
const PAGE_SIZE = 100;

/** Catálogo (clientes) e estoque (operação). */
@Injectable({ providedIn: 'root' })
export class InventoryApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${inject(APP_CONFIG).apiUrl}/api/v1`;

  private page<T>(path: string, cursor: string | null): Observable<Page<T>> {
    let params = new HttpParams().set('limit', PAGE_SIZE);
    if (cursor) {
      params = params.set('cursor', cursor);
    }
    return this.http.get<Page<T>>(`${this.base}/${path}`, { params });
  }

  /** Produtos à venda, juntando até {@link MAX_CATALOG_PAGES} páginas. */
  catalog(): Observable<CatalogItem[]> {
    let pages = 0;
    return this.page<CatalogItem>('catalog', null).pipe(
      expand((page) =>
        page.nextCursor && ++pages < MAX_CATALOG_PAGES
          ? this.page<CatalogItem>('catalog', page.nextCursor)
          : EMPTY,
      ),
      reduce((all, page) => all.concat(page.items), [] as CatalogItem[]),
    );
  }

  list(cursor: string | null = null): Observable<Page<StockItem>> {
    return this.page<StockItem>('inventory', cursor);
  }

  get(sku: string): Observable<StockItem> {
    return this.http.get<StockItem>(`${this.base}/inventory/${encodeURIComponent(sku)}`);
  }

  register(product: RegisterProduct): Observable<StockItem> {
    return this.http.post<StockItem>(`${this.base}/inventory`, product);
  }

  /** Só repõe se o produto ainda está na versão vista (`If-Match`); senão a API responde 412. */
  restock(item: Pick<StockItem, 'sku' | 'version'>, quantity: number): Observable<StockItem> {
    return this.http.post<StockItem>(
      `${this.base}/inventory/${encodeURIComponent(item.sku)}/restock`,
      { quantity },
      { headers: { 'If-Match': `"${item.version}"` } },
    );
  }
}
