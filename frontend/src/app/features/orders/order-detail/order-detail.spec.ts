import { TestBed } from '@angular/core/testing';
import { HttpTestingController, TestRequest } from '@angular/common/http/testing';
import { fireEvent, render, screen, waitFor } from '@testing-library/angular';
import { ORDER_POLL_INTERVAL_MS } from '../../../shared/order-poller';
import { API, ORDER_ID, anOrder, problem, testProviders } from '../../../../testing/test-support';
import { OrderDetail } from './order-detail';

describe('OrderDetail', () => {
  let http: HttpTestingController;
  const url = `${API}/api/v1/orders/${ORDER_ID}`;

  async function setup(state: unknown = null) {
    history.replaceState(state, '');
    await render(OrderDetail, {
      providers: [...testProviders(), { provide: ORDER_POLL_INTERVAL_MS, useValue: 0 }],
      inputs: { id: ORDER_ID },
    });
    http = TestBed.inject(HttpTestingController);
  }

  /** As consultas são sequenciais: a próxima sai depois da resposta anterior. */
  async function nextPoll(): Promise<TestRequest> {
    let request: TestRequest | undefined;
    await waitFor(() => {
      request = http.expectOne(url);
    });
    return request as TestRequest;
  }

  afterEach(() => {
    http.verify();
    history.replaceState(null, '');
  });

  it('follows the stock saga until the order is confirmed', async () => {
    await setup();

    (await nextPoll()).flush(anOrder({ status: 'CREATED' }));
    expect(await screen.findByText('Aguardando a reserva de estoque…')).toBeInTheDocument();
    (await nextPoll()).flush(anOrder({ status: 'CONFIRMED' }));

    expect(await screen.findByText('Status: Confirmado.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Cancelar pedido' })).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: 'Atualizar' })).toBeInTheDocument();
  });

  it('shows the just-created order while the read model catches up', async () => {
    await setup({ order: anOrder({ status: 'CREATED' }) });

    expect(screen.getByText('Aguardando estoque')).toBeInTheDocument();
    (await nextPoll()).flush(problem('order-not-found', 404), { status: 404, statusText: 'NF' });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    (await nextPoll()).flush(
      anOrder({
        status: 'CANCELLED',
        cancellationReason: 'Stock rejected: OUT_OF_STOCK (SKU-001)',
      }),
    );

    expect(await screen.findByText('Estoque insuficiente (SKU-001)')).toBeInTheDocument();
  });

  it('ignores navigation state that belongs to another order', async () => {
    await setup({ order: anOrder({ id: 'other' }) });

    expect(screen.queryByText('Aguardando estoque')).not.toBeInTheDocument();
    (await nextPoll()).flush(problem('order-not-found', 404), { status: 404, statusText: 'NF' });

    expect(await screen.findByRole('alert')).toHaveTextContent('Pedido não encontrado');
    // Ainda tenta de novo: a projeção pode estar atrasada.
    (await nextPoll()).flush(anOrder({ status: 'SHIPPED' }));
    expect(await screen.findByText('Enviado')).toBeInTheDocument();
  });

  it('cancels the order after confirmation, sending the optional reason', async () => {
    await setup();
    (await nextPoll()).flush(anOrder({ status: 'CREATED' }));
    const pending = await nextPoll();

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar pedido' }));
    await screen.findByRole('dialog');
    fireEvent.input(screen.getByLabelText('Motivo (opcional)'), {
      target: { value: 'comprei errado' },
    });
    fireEvent.click(
      screen.getAllByRole('button', { name: 'Cancelar pedido' }).at(-1) as HTMLElement,
    );

    const cancel = await waitFor(() => http.expectOne(`${url}/cancel`));
    expect(cancel.request.body).toEqual({ reason: 'comprei errado' });
    cancel.flush(anOrder({ status: 'CANCELLED', cancellationReason: 'comprei errado' }));
    expect(pending.cancelled).toBe(true);
    expect(await screen.findByText('Pedido cancelado.')).toBeInTheDocument();
    expect(screen.getByText('comprei errado')).toBeInTheDocument();
  });

  it('reports a cancel that arrives too late and reloads the order', async () => {
    await setup();
    (await nextPoll()).flush(anOrder({ status: 'CREATED' }));
    (await nextPoll()).flush(anOrder({ status: 'CREATED' }));
    const pollAfter = await nextPoll();

    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar pedido' }));
    await screen.findByRole('dialog');
    fireEvent.click(
      screen.getAllByRole('button', { name: 'Cancelar pedido' }).at(-1) as HTMLElement,
    );
    (await waitFor(() => http.expectOne(`${url}/cancel`))).flush(
      problem('order-state-conflict', 409),
      { status: 409, statusText: 'Conflict' },
    );

    expect(await screen.findByRole('alert')).toHaveTextContent('mudou de status');
    expect(pollAfter.cancelled).toBe(true);
    (await nextPoll()).flush(anOrder({ status: 'CONFIRMED' }));
    expect(await screen.findByText('Confirmado')).toBeInTheDocument();
  });

  it('keeps the order when the user gives up cancelling', async () => {
    await setup();
    (await nextPoll()).flush(anOrder({ status: 'CREATED' }));
    (await nextPoll()).flush(anOrder({ status: 'CONFIRMED' }));
    await screen.findByText('Status: Confirmado.');

    fireEvent.click(screen.getByRole('button', { name: 'Atualizar' }));
    (await nextPoll()).flush(anOrder({ status: 'CREATED' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Cancelar pedido' }));
    await screen.findByRole('dialog');
    fireEvent.click(screen.getByRole('button', { name: 'Voltar' }));

    (await nextPoll()).flush(anOrder({ status: 'CONFIRMED' }));
    http.expectNone(`${url}/cancel`);
  });
});
