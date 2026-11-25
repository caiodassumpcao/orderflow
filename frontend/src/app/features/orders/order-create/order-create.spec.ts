import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { fireEvent, render, screen, within } from '@testing-library/angular';
import { IDEMPOTENCY_KEY_FACTORY } from '../../../core/api/idempotency';
import {
  API,
  aCatalogItem,
  anOrder,
  problem,
  testProviders,
} from '../../../../testing/test-support';
import { OrderCreate } from './order-create';

describe('OrderCreate', () => {
  let http: HttpTestingController;
  let keys: number;

  const catalog = [
    aCatalogItem({ sku: 'SKU-001', name: 'Caneca', unitPrice: 19.9 }),
    aCatalogItem({ sku: 'SKU-002', name: 'Fone', unitPrice: 100 }),
    aCatalogItem({ sku: 'SKU-003', name: 'Esgotado', inStock: false }),
    aCatalogItem({ sku: 'USD-001', name: 'Importado', currency: 'USD', unitPrice: 5 }),
  ];

  async function setup(catalogItems = catalog) {
    keys = 0;
    await render(OrderCreate, {
      providers: [
        ...testProviders(),
        { provide: IDEMPOTENCY_KEY_FACTORY, useValue: () => `key-${++keys}` },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url === `${API}/api/v1/catalog`)
      .flush({ items: catalogItems, nextCursor: null });
    await screen.findByRole('group', { name: 'Item 1' });
  }

  const productSelect = (line: number) =>
    within(screen.getByRole('group', { name: `Item ${line}` })).getByLabelText('Produto');
  const quantityInput = (line: number) =>
    within(screen.getByRole('group', { name: `Item ${line}` })).getByLabelText('Quantidade');
  const choose = (line: number, sku: string, quantity: number) => {
    fireEvent.change(productSelect(line), { target: { value: sku } });
    fireEvent.input(quantityInput(line), { target: { value: String(quantity) } });
  };
  const submit = () => fireEvent.click(screen.getByRole('button', { name: 'Criar pedido' }));

  afterEach(() => http.verify());

  it('shows catalog prices, disables products without stock and totals the order', async () => {
    await setup();

    expect(screen.getByRole('option', { name: /Esgotado.*sem estoque/ })).toBeDisabled();
    choose(1, 'SKU-001', 3);
    fireEvent.click(screen.getByRole('button', { name: 'Adicionar item' }));
    choose(2, 'SKU-002', 1);

    expect(await screen.findByText(/R\$\s*159,70/)).toBeInTheDocument();
  });

  it('places the order at the catalog price and opens its detail', async () => {
    await setup();
    const navigate = jest.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    choose(1, 'SKU-002', 2);

    submit();

    const request = http.expectOne(`${API}/api/v1/orders`);
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-1');
    expect(request.request.body).toEqual({
      currency: 'BRL',
      items: [{ sku: 'SKU-002', quantity: 2, unitPrice: 100 }],
    });
    const order = anOrder({ id: 'new-order' });
    request.flush(order);
    await screen.findByText(/Pedido criado/);
    expect(navigate).toHaveBeenCalledWith(['/pedidos', 'new-order'], { state: { order } });
  });

  it('reuses the idempotency key when retrying the same order, and renews it after changes', async () => {
    await setup();
    choose(1, 'SKU-001', 1);

    submit();
    http.expectOne(`${API}/api/v1/orders`).error(new ProgressEvent('error'));
    expect(await screen.findByRole('alert')).toHaveTextContent('conexão');
    submit();
    const retry = http.expectOne(`${API}/api/v1/orders`);
    expect(retry.request.headers.get('Idempotency-Key')).toBe('key-1');
    retry.flush(problem('order-state-conflict', 409), { status: 409, statusText: 'Conflict' });

    fireEvent.input(quantityInput(1), { target: { value: '2' } });
    submit();
    expect(http.expectOne(`${API}/api/v1/orders`).request.headers.get('Idempotency-Key')).toBe(
      'key-2',
    );
  });

  it('asks for a new key when the API says the key was used with other content', async () => {
    await setup();
    choose(1, 'SKU-001', 1);
    submit();
    http
      .expectOne(`${API}/api/v1/orders`)
      .flush(problem('idempotency-key-reused', 422), { status: 422, statusText: 'Unprocessable' });
    expect(await screen.findByRole('alert')).toHaveTextContent('outros dados');

    submit();

    expect(http.expectOne(`${API}/api/v1/orders`).request.headers.get('Idempotency-Key')).toBe(
      'key-2',
    );
  });

  it('blocks invalid orders before calling the API', async () => {
    await setup();

    submit();
    expect(await screen.findByText('Escolha um produto.')).toBeInTheDocument();

    choose(1, 'SKU-001', 1);
    fireEvent.click(screen.getByRole('button', { name: 'Adicionar item' }));
    choose(2, 'SKU-001', 2);
    submit();
    expect(await screen.findByText(/só pode aparecer uma vez/)).toBeInTheDocument();

    choose(2, 'USD-001', 1);
    submit();
    expect(await screen.findByText(/mesma moeda/)).toBeInTheDocument();

    choose(2, 'SKU-002', 0);
    submit();
    expect(await screen.findByText(/número inteiro de 1 a 1000/)).toBeInTheDocument();
    http.expectNone(`${API}/api/v1/orders`);
  });

  it('removes lines but always keeps one', async () => {
    await setup();
    fireEvent.click(screen.getByRole('button', { name: 'Adicionar item' }));
    expect(screen.getAllByRole('group', { name: /Item \d/ })).toHaveLength(2);

    fireEvent.click(screen.getByRole('button', { name: 'Remover item 2' }));

    expect(screen.getAllByRole('group', { name: /Item \d/ })).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Remover item 1' })).toBeDisabled();
  });

  it('explains an empty or unavailable catalog', async () => {
    await render(OrderCreate, { providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    http
      .expectOne((r) => r.url === `${API}/api/v1/catalog`)
      .flush(null, { status: 500, statusText: 'Error' });

    expect(await screen.findByRole('alert')).toHaveTextContent('servidor');
    expect(screen.queryByRole('button', { name: 'Criar pedido' })).not.toBeInTheDocument();
  });

  it('says when there are no products', async () => {
    await render(OrderCreate, { providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    http.expectOne((r) => r.url === `${API}/api/v1/catalog`).flush({ items: [], nextCursor: null });

    expect(await screen.findByText(/Não há produtos/)).toBeInTheDocument();
  });
});
