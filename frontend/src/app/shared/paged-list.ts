import { DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable } from 'rxjs';
import { Page } from '../core/api/models';
import { ApiError, toApiError } from '../core/http/api-error';

/**
 * Estado de uma listagem com paginação por cursor ("Carregar mais"): itens acumulados, cursor da
 * próxima página, carregamento e erro. Criado no contexto de injeção de um componente.
 */
export class PagedList<T> {
  readonly items = signal<readonly T[]>([]);
  readonly nextCursor = signal<string | null>(null);
  readonly loading = signal(false);
  readonly loaded = signal(false);
  readonly error = signal<ApiError | null>(null);

  private readonly destroyRef = inject(DestroyRef);

  constructor(private readonly fetchPage: (cursor: string | null) => Observable<Page<T>>) {}

  /** Recomeça da primeira página (ex.: filtros mudaram). */
  reload(): void {
    this.load(null, true);
  }

  loadMore(): void {
    const cursor = this.nextCursor();
    if (cursor && !this.loading()) {
      this.load(cursor, false);
    }
  }

  private load(cursor: string | null, replace: boolean): void {
    this.loading.set(true);
    this.error.set(null);
    this.fetchPage(cursor)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          this.items.update((current) => (replace ? page.items : [...current, ...page.items]));
          this.nextCursor.set(page.nextCursor);
          this.loading.set(false);
          this.loaded.set(true);
        },
        error: (error: unknown) => {
          this.error.set(toApiError(error));
          this.loading.set(false);
        },
      });
  }
}
