import { InjectionToken } from '@angular/core';

/** Gera chaves de idempotência (UUID v4); substituível nos testes. */
export const IDEMPOTENCY_KEY_FACTORY = new InjectionToken<() => string>('IDEMPOTENCY_KEY_FACTORY', {
  providedIn: 'root',
  factory: () => () => crypto.randomUUID(),
});
