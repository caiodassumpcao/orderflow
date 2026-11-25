import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { Injector, runInInjectionContext } from '@angular/core';
import { render, screen } from '@testing-library/angular';
import { Subject, of, throwError } from 'rxjs';
import { Page } from '../core/api/models';
import { toApiError } from '../core/http/api-error';
import { anOrder, testProviders } from '../../testing/test-support';
import { describeCancellation } from './cancellation-reason';
import { ErrorAlert } from './error-alert/error-alert';
import { OrderDetails } from './order-details/order-details';
import { MAX_POLLS, pollOrder } from './order-poller';
import { OrderStatusBadge, isAwaitingStock } from './order-status/order-status';
import { PagedList } from './paged-list';

describe('OrderStatusBadge', () => {
  it.each([
    ['CREATED', 'Aguardando estoque', 'pending'],
    ['STOCK_RESERVED', 'Estoque reservado', 'pending'],
    ['CONFIRMED', 'Confirmado', 'confirmed'],
    ['SHIPPED', 'Enviado', 'shipped'],
    ['CANCELLED', 'Cancelado', 'cancelled'],
  ] as const)('shows %s as text, with color only as reinforcement', async (status, label, tone) => {
    await render(OrderStatusBadge, { inputs: { status } });

    expect(screen.getByText(label)).toHaveClass('badge', tone);
  });

  it('knows which statuses still wait for the stock saga', () => {
    expect(isAwaitingStock('CREATED')).toBe(true);
    expect(isAwaitingStock('STOCK_RESERVED')).toBe(true);
    expect(isAwaitingStock('CONFIRMED')).toBe(false);
  });
});

describe('describeCancellation', () => {
  it.each([
    [null, null],
    ['', null],
    ['Stock rejected: OUT_OF_STOCK (SKU-005)', 'Estoque insuficiente (SKU-005)'],
    [
      'Stock rejected: UNKNOWN_SKU (SKU-404, SKU-405)',
      'Produto inexistente no catálogo (SKU-404, SKU-405)',
    ],
    ['Stock rejected: PRICE_MISMATCH', 'Preço ou moeda diferentes do catálogo'],
    ['Stock rejected: SOMETHING_NEW', 'Recusado pelo estoque'],
    ['mudei de ideia', 'mudei de ideia'],
  ])('describes %p', (reason, expected) => {
    expect(describeCancellation(reason)).toBe(expected);
  });
});

describe('ErrorAlert', () => {
  it('renders nothing without an error', async () => {
    await render(ErrorAlert, { inputs: { error: null } });

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('announces the message and shows the support code only for server errors', async () => {
    const serverError = toApiError(
      new HttpErrorResponse({ status: 500, error: { correlationId: 'abc-123' } }),
    );
    const { rerender } = await render(ErrorAlert, { inputs: { error: serverError } });

    expect(screen.getByRole('alert')).toHaveTextContent('servidor encontrou um erro');
    expect(screen.getByText(/abc-123/)).toBeInTheDocument();

    await rerender({
      inputs: {
        error: toApiError(new HttpErrorResponse({ status: 404, error: { correlationId: 'x' } })),
      },
    });
    expect(screen.queryByText(/Código para suporte/)).not.toBeInTheDocument();
  });
});

describe('OrderDetails', () => {
  it('shows the status, totals, items and the translated cancellation reason', async () => {
    await render(OrderDetails, {
      providers: testProviders(),
      inputs: {
        order: anOrder({
          status: 'CANCELLED',
          cancellationReason: 'Stock rejected: OUT_OF_STOCK (SKU-001)',
        }),
      },
    });

    expect(screen.getByText('Cancelado')).toBeInTheDocument();
    expect(screen.getByText('Estoque insuficiente (SKU-001)')).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: 'SKU-001' })).toBeInTheDocument();
    expect(screen.getAllByText(/R\$\s*39,80/).length).toBeGreaterThan(1);
  });

  it('hides the cancellation reason of active orders', async () => {
    await render(OrderDetails, { providers: testProviders(), inputs: { order: anOrder() } });

    expect(screen.queryByText('Motivo do cancelamento')).not.toBeInTheDocument();
  });
});

describe('PagedList', () => {
  function list(fetch: (cursor: string | null) => ReturnType<typeof of<Page<number>>>) {
    TestBed.configureTestingModule({});
    return runInInjectionContext(TestBed.inject(Injector), () => new PagedList<number>(fetch));
  }

  it('loads the first page and appends the next ones', () => {
    const fetch = jest.fn((cursor: string | null) =>
      of(cursor ? { items: [3], nextCursor: null } : { items: [1, 2], nextCursor: 'c1' }),
    );
    const paged = list(fetch);

    paged.reload();
    paged.loadMore();
    paged.loadMore();

    expect(paged.items()).toEqual([1, 2, 3]);
    expect(paged.nextCursor()).toBeNull();
    expect(paged.loaded()).toBe(true);
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it('replaces the items when reloaded', () => {
    const paged = list(() => of({ items: [1], nextCursor: null }));

    paged.reload();
    paged.reload();

    expect(paged.items()).toEqual([1]);
  });

  it('keeps the error and stops loading on failure', () => {
    const paged = list(() => throwError(() => new HttpErrorResponse({ status: 503 })));

    paged.reload();

    expect(paged.error()?.status).toBe(503);
    expect(paged.loading()).toBe(false);
  });

  it('ignores load more while a page is loading', () => {
    const pending = new Subject<Page<number>>();
    const fetch = jest.fn(() => pending);
    const paged = list(fetch);
    paged.nextCursor.set('c1');
    paged.loading.set(true);

    paged.loadMore();

    expect(fetch).not.toHaveBeenCalled();
  });
});

describe('pollOrder', () => {
  beforeEach(() => jest.useFakeTimers());
  afterEach(() => jest.useRealTimers());

  it('polls until the stock saga finishes', () => {
    const statuses = ['CREATED', 'STOCK_RESERVED', 'CONFIRMED'] as const;
    let call = 0;
    const results: string[] = [];
    let completed = false;

    pollOrder(
      () => of(anOrder({ status: statuses[call++] })),
      (order) => order,
      100,
    ).subscribe({
      next: (result) => results.push('value' in result ? result.value.status : 'error'),
      complete: () => (completed = true),
    });
    jest.advanceTimersByTime(1000);

    expect(results).toEqual(['CREATED', 'STOCK_RESERVED', 'CONFIRMED']);
    expect(completed).toBe(true);
  });

  it('retries while the read model does not have the order yet', () => {
    let call = 0;
    const results: string[] = [];

    pollOrder(
      () =>
        call++ < 2
          ? throwError(() => new HttpErrorResponse({ status: 404 }))
          : of(anOrder({ status: 'CONFIRMED' })),
      (order) => order,
      100,
    ).subscribe((result) => results.push('value' in result ? result.value.status : '404'));
    jest.advanceTimersByTime(1000);

    expect(results).toEqual(['404', '404', 'CONFIRMED']);
  });

  it('stops at other errors and after the maximum number of polls', () => {
    const errors: number[] = [];
    pollOrder(
      () => throwError(() => new HttpErrorResponse({ status: 500 })),
      (order) => order,
      100,
    ).subscribe((result) => errors.push('error' in result ? result.error.status : 0));
    let pending = 0;
    pollOrder(
      () => of(anOrder()),
      (order) => order,
      100,
    ).subscribe(() => pending++);
    jest.advanceTimersByTime(100 * (MAX_POLLS + 5));

    expect(errors).toEqual([500]);
    expect(pending).toBe(MAX_POLLS);
  });
});
