import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/angular';
import { API, anAdminSummary, testProviders } from '../../../../testing/test-support';
import { AdminOrderList } from './admin-order-list';

describe('AdminOrderList', () => {
  let http: HttpTestingController;

  async function setup() {
    await render(AdminOrderList, { providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  }

  const listRequest = () => http.expectOne((r) => r.url === `${API}/api/v1/admin/orders`);
  const table = () => within(screen.getByRole('table'));
  const filter = (name: string) =>
    within(screen.getByRole('group', { name: 'Filtrar por status' })).getByRole('button', {
      name,
    });

  afterEach(() => http.verify());

  it('lists orders of every customer, in a table and in cards', async () => {
    await setup();
    listRequest().flush({ items: [anAdminSummary({ customerId: 'alice' })], nextCursor: 'c1' });

    expect(await screen.findByRole('cell', { name: 'alice' })).toBeInTheDocument();
    expect(table().getByRole('link', { name: /Pedido 01a20f13/ })).toHaveAttribute(
      'href',
      '/admin/pedidos/01a20f13-6a05-73f7-9b74-d102e42e88ab',
    );
    expect(
      within(screen.getByRole('list', { name: 'Pedidos de todos os clientes' })).getByText(/alice/),
    ).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Carregar mais' }));
    const more = listRequest();
    expect(more.request.params.get('cursor')).toBe('c1');
    more.flush({ items: [], nextCursor: null });
  });

  it('filters by status at once and by customer on search, and clears the customer', async () => {
    await setup();
    listRequest().flush({ items: [], nextCursor: null });
    expect(await screen.findByText('Nenhum pedido encontrado.')).toBeInTheDocument();

    fireEvent.click(filter('Confirmado'));
    const byStatus = listRequest();
    expect(byStatus.request.params.get('status')).toBe('CONFIRMED');
    byStatus.flush({ items: [], nextCursor: null });

    fireEvent.input(screen.getByLabelText('Cliente (ID)'), { target: { value: ' bob ' } });
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));
    const byCustomer = listRequest();
    expect(byCustomer.request.params.get('status')).toBe('CONFIRMED');
    expect(byCustomer.request.params.get('customerId')).toBe('bob');
    byCustomer.flush({ items: [], nextCursor: null });

    fireEvent.click(screen.getByRole('button', { name: 'Limpar' }));
    const cleared = listRequest();
    expect(cleared.request.params.has('customerId')).toBe(false);
    cleared.flush({ items: [], nextCursor: null });
  });

  it('does not search with an invalid customer id', async () => {
    await setup();
    listRequest().flush({ items: [], nextCursor: null });

    fireEvent.input(screen.getByLabelText('Cliente (ID)'), { target: { value: 'not valid!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Buscar' }));

    await waitFor(() => expect(screen.getByText(/Use até 64 letras/)).toBeInTheDocument());
    http.expectNone((r) => r.url === `${API}/api/v1/admin/orders`);
  });
});
