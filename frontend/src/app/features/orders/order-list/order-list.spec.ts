import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/angular';
import { API, aSummary, testProviders } from '../../../../testing/test-support';
import { OrderList } from './order-list';

describe('OrderList', () => {
  let http: HttpTestingController;

  async function setup() {
    await render(OrderList, { providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  }

  const listRequest = (status?: string, cursor?: string) =>
    http.expectOne(
      (r) =>
        r.url === `${API}/api/v1/orders` &&
        r.params.get('status') === (status ?? null) &&
        r.params.get('cursor') === (cursor ?? null),
    );

  /** A tabela (telas largas); a lista em cards tem o mesmo conteúdo para o celular. */
  const table = () => within(screen.getByRole('table'));
  const filter = (name: string) =>
    within(screen.getByRole('group', { name: 'Filtrar por status' })).getByRole('button', {
      name,
    });

  afterEach(() => http.verify());

  it('lists the customer orders with status, total and a link to the detail', async () => {
    await setup();
    listRequest().flush({ items: [aSummary({ status: 'SHIPPED' })], nextCursor: null });

    const link = await waitFor(() => table().getByRole('link', { name: /Pedido 01a20f13/ }));
    expect(link).toHaveAttribute('href', '/pedidos/01a20f13-6a05-73f7-9b74-d102e42e88ab');
    expect(table().getByText('Enviado')).toBeInTheDocument();
    expect(table().getByText(/R\$\s*39,80/)).toBeInTheDocument();
    expect(
      within(screen.getByRole('list', { name: /Pedidos, do mais novo/ })).getByText('1 item', {
        exact: false,
      }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Carregar mais' })).not.toBeInTheDocument();
  });

  it('invites the customer to place the first order when there is none', async () => {
    await setup();
    listRequest().flush({ items: [], nextCursor: null });

    expect(await screen.findByText(/ainda não fez pedidos/)).toBeInTheDocument();
  });

  it('filters by status with toggle chips and loads more pages with the cursor', async () => {
    await setup();
    listRequest().flush({ items: [aSummary()], nextCursor: null });
    expect(filter('Todos')).toHaveAttribute('aria-pressed', 'true');

    fireEvent.click(filter('Cancelado'));
    listRequest('CANCELLED').flush({ items: [aSummary({ id: 'a1' })], nextCursor: 'next-1' });
    await waitFor(() => expect(filter('Cancelado')).toHaveAttribute('aria-pressed', 'true'));
    fireEvent.click(filter('Cancelado'));
    fireEvent.click(await screen.findByRole('button', { name: 'Carregar mais' }));
    listRequest('CANCELLED', 'next-1').flush({ items: [aSummary({ id: 'a2' })], nextCursor: null });

    await waitFor(() => expect(table().getAllByRole('link', { name: /Pedido a/ })).toHaveLength(2));
  });

  it('explains an empty filter and shows API errors', async () => {
    await setup();
    listRequest().flush(null, { status: 503, statusText: 'Unavailable' });

    expect(await screen.findByRole('alert')).toHaveTextContent('servidor encontrou um erro');

    fireEvent.click(filter('Enviado'));
    listRequest('SHIPPED').flush({ items: [], nextCursor: null });
    expect(await screen.findByText('Nenhum pedido com este status.')).toBeInTheDocument();
  });
});
