import { InjectionToken } from '@angular/core';
import { Observable, catchError, defer, map, of, repeat, takeWhile } from 'rxjs';
import { Order } from '../core/api/models';
import { ApiError, toApiError } from '../core/http/api-error';
import { isAwaitingStock } from './order-status/order-status';

/** Intervalo entre consultas enquanto a saga de estoque não termina. */
export const ORDER_POLL_INTERVAL_MS = new InjectionToken<number>('ORDER_POLL_INTERVAL_MS', {
  providedIn: 'root',
  factory: () => 1500,
});

/** Desiste de acompanhar depois de tantas consultas (a tela oferece "Atualizar"). */
export const MAX_POLLS = 20;

export type PollResult<T> = { readonly value: T } | { readonly error: ApiError };

/**
 * Consulta o pedido até o status sair de "aguardando estoque". As consultas são sequenciais: a
 * próxima só sai {@code intervalMs} depois da resposta anterior, então uma API lenta nunca acumula
 * nem cancela requisições. O modelo de leitura é eventualmente consistente: logo após a criação o
 * pedido pode ainda não existir nele (404), e isso também é tentado de novo, até {@link MAX_POLLS}
 * vezes.
 */
export function pollOrder<T>(
  fetch: () => Observable<T>,
  orderOf: (value: T) => Order,
  intervalMs: number,
): Observable<PollResult<T>> {
  let polls = 0;
  return defer(() =>
    fetch().pipe(
      map((value): PollResult<T> => ({ value })),
      catchError((error: unknown) => of<PollResult<T>>({ error: toApiError(error) })),
    ),
  ).pipe(
    repeat({ delay: intervalMs }),
    takeWhile((result) => {
      polls++;
      const pending =
        'value' in result
          ? isAwaitingStock(orderOf(result.value).status)
          : result.error.status === 404;
      return pending && polls < MAX_POLLS;
    }, true),
  );
}
