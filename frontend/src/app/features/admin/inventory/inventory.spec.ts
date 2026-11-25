import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/angular';
import { API, aStockItem, problem, testProviders } from '../../../../testing/test-support';
import { Inventory } from './inventory';

describe('Inventory', () => {
  let http: HttpTestingController;

  async function setup() {
    await render(Inventory, { providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  }

  const listRequest = () =>
    http.expectOne((r) => r.url === `${API}/api/v1/inventory` && r.method === 'GET');

  afterEach(() => http.verify());

  it('lists products with price and quantities, flagging sold out ones', async () => {
    await setup();
    listRequest().flush({
      items: [aStockItem(), aStockItem({ sku: 'SKU-005', name: 'Adesivos', available: 0 })],
      nextCursor: 'SKU-005',
    });

    const row = (await screen.findByRole('rowheader', { name: 'Adesivos' })).closest(
      'tr',
    ) as HTMLElement;
    expect(within(row).getByText('(esgotado)')).toBeInTheDocument();
    expect(screen.getAllByText(/R\$\s*19,90/)).toHaveLength(2);

    fireEvent.click(screen.getByRole('button', { name: 'Carregar mais' }));
    const more = listRequest();
    expect(more.request.params.get('cursor')).toBe('SKU-005');
    more.flush({ items: [], nextCursor: null });
  });

  it('restocks a product through the dialog and updates its row', async () => {
    await setup();
    listRequest().flush({ items: [aStockItem({ version: 3 })], nextCursor: null });

    fireEvent.click(await screen.findByRole('button', { name: /Repor Caneca/ }));
    fireEvent.input(await screen.findByLabelText('Unidades a repor'), { target: { value: '5' } });
    fireEvent.click(screen.getByRole('button', { name: 'Repor' }));
    const restock = await waitFor(() => http.expectOne(`${API}/api/v1/inventory/SKU-001/restock`));
    expect(restock.request.headers.get('If-Match')).toBe('"3"');
    restock.flush(aStockItem({ available: 15, version: 4 }));

    expect(await screen.findByText(/15 disponíveis/)).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '15' })).toBeInTheDocument();
  });

  it('registers a product and reloads the list', async () => {
    await setup();
    listRequest().flush({ items: [], nextCursor: null });
    expect(await screen.findByText('Nenhum produto cadastrado.')).toBeInTheDocument();

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'SKU-NEW' } });
    fireEvent.input(screen.getByLabelText('Nome'), { target: { value: ' Garrafa ' } });
    fireEvent.input(screen.getByLabelText('Preço unitário'), { target: { value: '89.9' } });
    fireEvent.input(screen.getByLabelText('Estoque inicial'), { target: { value: '12' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cadastrar produto' }));

    const register = http.expectOne(
      (r) => r.url === `${API}/api/v1/inventory` && r.method === 'POST',
    );
    expect(register.request.body).toEqual({
      sku: 'SKU-NEW',
      name: 'Garrafa',
      currency: 'BRL',
      unitPrice: 89.9,
      initialQuantity: 12,
    });
    register.flush(aStockItem({ sku: 'SKU-NEW', name: 'Garrafa' }));
    expect(await screen.findByText('Produto Garrafa cadastrado.')).toBeInTheDocument();
    listRequest().flush({
      items: [aStockItem({ sku: 'SKU-NEW', name: 'Garrafa' })],
      nextCursor: null,
    });
  });

  it('reports a duplicate SKU and does not submit an invalid form', async () => {
    await setup();
    listRequest().flush({ items: [], nextCursor: null });

    fireEvent.click(await screen.findByRole('button', { name: 'Cadastrar produto' }));
    expect(await screen.findByText(/De 3 a 32 letras/)).toBeInTheDocument();
    http.expectNone((r) => r.method === 'POST');

    fireEvent.input(screen.getByLabelText('SKU'), { target: { value: 'SKU-001' } });
    fireEvent.input(screen.getByLabelText('Nome'), { target: { value: 'Caneca' } });
    fireEvent.input(screen.getByLabelText('Preço unitário'), { target: { value: '19.9' } });
    fireEvent.click(screen.getByRole('button', { name: 'Cadastrar produto' }));
    http
      .expectOne((r) => r.method === 'POST')
      .flush(problem('stock-item-already-exists', 409), { status: 409, statusText: 'Conflict' });

    expect(await screen.findByRole('alert')).toHaveTextContent('Já existe um produto com este SKU');
  });
});
