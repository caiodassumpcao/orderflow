import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { fireEvent, render, screen, waitFor } from '@testing-library/angular';
import { API, anAdminSummary, testProviders } from '../../../../testing/test-support';
import { AdminOrderList } from './admin-order-list';

describe('AdminOrderList', () => {
  let http: HttpTestingController;

  async function setup() {
    await render(AdminOrderList, { providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  }

  const listRequest = () => http.expectOne((r) => r.url === `${API}/api/v1/admin/orders`);

  afterEach(() => http.verify());

  it('lists orders of every customer', async () => {
    await setup();
    listRequest().flush({ items: [anAdminSummary({ customerId: 'alice' })], nextCursor: 'c1' });

    expect(await screen.findByRole('cell', { name: 'alice' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Pedido 01a20f13/ })).toHaveAttribute(
      'href',
      '/admin/pedidos/01a20f13-6a05-73f7-9b74-d102e42e88ab',
    );

    fireEvent.click(screen.getByRole('button', { name: 'Carregar mais' }));
    const more = listRequest();
    expect(more.request.params.get('cursor')).toBe('c1');
    more.flush({ items: [], nextCursor: null });
  });

  it('filters by status and customer, and clears the filters', async () => {
    await setup();
    listRequest().flush({ items: [], nextCursor: null });
    expect(await screen.findByText('Nenhum pedido encontrado.')).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'CONFIRMED' } });
    fireEvent.input(screen.getByLabelText('Cliente (ID)'), { target: { value: 'bob' } });
    fireEvent.click(screen.getByRole('button', { name: 'Filtrar' }));
    const filtered = listRequest();
    expect(filtered.request.params.get('status')).toBe('CONFIRMED');
    expect(filtered.request.params.get('customerId')).toBe('bob');
    filtered.flush({ items: [], nextCursor: null });

    fireEvent.click(screen.getByRole('button', { name: 'Limpar' }));
    const cleared = listRequest();
    expect(cleared.request.params.keys()).toEqual([]);
    cleared.flush({ items: [], nextCursor: null });
  });

  it('does not search with an invalid customer id', async () => {
    await setup();
    listRequest().flush({ items: [], nextCursor: null });

    fireEvent.input(screen.getByLabelText('Cliente (ID)'), { target: { value: 'not valid!' } });
    fireEvent.click(screen.getByRole('button', { name: 'Filtrar' }));

    await waitFor(() => expect(screen.getByText(/Use até 64 letras/)).toBeInTheDocument());
    http.expectNone((r) => r.url === `${API}/api/v1/admin/orders`);
  });
});
