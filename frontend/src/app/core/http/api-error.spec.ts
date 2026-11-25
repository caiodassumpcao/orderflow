import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { problem } from '../../../testing/test-support';
import { toApiError } from './api-error';

function httpError(status: number, body: unknown = null, headers: Record<string, string> = {}) {
  return new HttpErrorResponse({ status, error: body, headers: new HttpHeaders(headers) });
}

describe('toApiError', () => {
  it('translates catalogued problem types', () => {
    const error = toApiError(
      httpError(409, problem('order-state-conflict', 409, { currentStatus: 'SHIPPED' })),
    );

    expect(error).toEqual(
      expect.objectContaining({
        status: 409,
        problemType: 'order-state-conflict',
        message: 'O pedido mudou de status e a operação não é mais permitida.',
        correlationId: 'corr-1',
      }),
    );
  });

  it('keeps the invalid fields of a validation error', () => {
    const error = toApiError(
      httpError(
        400,
        problem('validation-error', 400, {
          errors: [{ field: 'items[0].quantity', message: 'must be ≥ 1' }, { bogus: true }],
        }),
      ),
    );

    expect(error.message).toContain('campos');
    expect(error.fieldErrors).toEqual([{ field: 'items[0].quantity', message: 'must be ≥ 1' }]);
  });

  it('tells how long to wait when rate limited', () => {
    const error = toApiError(
      httpError(429, problem('rate-limit-exceeded', 429), { 'Retry-After': '12' }),
    );

    expect(error.retryAfterSeconds).toBe(12);
    expect(error.message).toContain('12 s');
  });

  it.each([
    [0, 'conexão'],
    [400, 'inválidos'],
    [401, 'sessão expirou'],
    [403, 'permissão'],
    [404, 'não existe'],
    [429, 'Aguarde'],
    [500, 'servidor encontrou um erro'],
    [418, 'Não foi possível concluir'],
  ])('falls back to a message by status %i', (status, text) => {
    expect(toApiError(httpError(status)).message).toContain(text);
  });

  it('ignores problem types from other catalogs and reads the correlation header', () => {
    const error = toApiError(
      httpError(503, { type: 'https://example.test/other' }, { 'X-Correlation-Id': 'corr-h' }),
    );

    expect(error.problemType).toBeNull();
    expect(error.correlationId).toBe('corr-h');
  });

  it('handles failures that are not HTTP responses', () => {
    expect(toApiError(new Error('boom'))).toEqual(
      expect.objectContaining({ status: 0, problemType: null, fieldErrors: [] }),
    );
  });
});
