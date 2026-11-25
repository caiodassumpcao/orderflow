import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { fireEvent, render, screen, waitFor } from '@testing-library/angular';
import { API, ORDER_ID, anOrder, problem, testProviders } from '../../../../testing/test-support';
import { AdminOrderDetail } from './admin-order-detail';

describe('AdminOrderDetail', () => {
  let http: HttpTestingController;
  const url = `${API}/api/v1/admin/orders/${ORDER_ID}`;

  async function setup() {
    await render(AdminOrderDetail, { providers: testProviders(), inputs: { id: ORDER_ID } });
    http = TestBed.inject(HttpTestingController);
  }

  async function confirmShipping() {
    fireEvent.click(await screen.findByRole('button', { name: 'Marcar como enviado' }));
    await screen.findByRole('dialog');
    fireEvent.click(
      screen.getAllByRole('button', { name: 'Marcar como enviado' }).at(-1) as HTMLElement,
    );
    return waitFor(() => http.expectOne(`${API}/api/v1/orders/${ORDER_ID}/ship`));
  }

  afterEach(() => http.verify());

  it('shows the customer and ships a confirmed order', async () => {
    await setup();
    http.expectOne(url).flush({ customerId: 'alice', order: anOrder({ status: 'CONFIRMED' }) });
    expect(await screen.findByText('alice')).toBeInTheDocument();

    (await confirmShipping()).flush(anOrder({ status: 'SHIPPED' }));

    expect(await screen.findByText('Pedido marcado como enviado.')).toBeInTheDocument();
    // O selo do status; a linha do tempo também tem o passo "Enviado".
    expect(screen.getByText('Enviado', { selector: '.badge' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Marcar como enviado' })).not.toBeInTheDocument();
  });

  it('reports a shipping conflict', async () => {
    await setup();
    http.expectOne(url).flush({ customerId: 'alice', order: anOrder({ status: 'CONFIRMED' }) });

    (await confirmShipping()).flush(problem('order-state-conflict', 409), {
      status: 409,
      statusText: 'Conflict',
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('mudou de status');
  });

  it('offers shipping only for confirmed orders and reloads on demand', async () => {
    await setup();
    http.expectOne(url).flush({ customerId: 'alice', order: anOrder({ status: 'CREATED' }) });
    await screen.findByText('alice');

    expect(screen.queryByRole('button', { name: 'Marcar como enviado' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Atualizar' }));
    http.expectOne(url).flush(problem('order-not-found', 404), { status: 404, statusText: 'NF' });

    expect(await screen.findByRole('alert')).toHaveTextContent('Pedido não encontrado');
  });
});
