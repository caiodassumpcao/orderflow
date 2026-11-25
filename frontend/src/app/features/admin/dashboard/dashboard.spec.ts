import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/angular';
import { StockItem } from '../../../core/api/models';
import { API, aDashboard, aStockItem, testProviders } from '../../../../testing/test-support';
import { DASHBOARD_DAYS, Dashboard, browserTimeZone } from './dashboard';

describe('Dashboard', () => {
  let http: HttpTestingController;

  const stock: StockItem[] = [
    aStockItem({ sku: 'SKU-001', name: 'Caneca', available: 50 }),
    aStockItem({ sku: 'SKU-004', name: 'Mochila', available: 3 }),
    aStockItem({ sku: 'SKU-005', name: 'Adesivos', available: 0 }),
  ];

  async function setup() {
    await render(Dashboard, { providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  }

  const summaryRequest = (days = DASHBOARD_DAYS) =>
    http.expectOne(
      (r) =>
        r.url === `${API}/api/v1/dashboard/summary` &&
        r.params.get('days') === String(days) &&
        r.params.get('timeZone') === browserTimeZone(),
    );
  const stockRequest = () =>
    http.expectOne((r) => r.url === `${API}/api/v1/inventory` && r.method === 'GET');

  afterEach(() => http.verify());

  it('shows the indicators, the orders per day and by status', async () => {
    await setup();
    summaryRequest().flush(aDashboard());
    stockRequest().flush({ items: stock, nextCursor: null });

    const indicators = await screen.findByRole('list', { name: 'Indicadores' });
    // Pedidos no período = soma da série diária (4 + 2); o total geral vem no rodapé do card
    expect(within(indicators).getByText('6')).toBeInTheDocument();
    expect(within(indicators).getByText('9 no total')).toBeInTheDocument();
    expect(within(indicators).getByText(/R\$\s*150,50/)).toBeInTheDocument();
    // Aguardando estoque = CREATED + STOCK_RESERVED; prontos para envio = CONFIRMED
    expect(within(indicators).getByText('2')).toBeInTheDocument();
    expect(within(indicators).getByText('3')).toBeInTheDocument();

    const perDay = screen.getByRole('list', { name: 'Pedidos e cancelados por dia' });
    expect(within(perDay).getAllByRole('listitem')).toHaveLength(2);
    expect(within(perDay).getByText(/24\/11: 4 pedidos, 1 cancelado/)).toBeInTheDocument();
    expect(within(perDay).getByText(/\(hoje\): 2 pedidos, 0 cancelados/)).toBeInTheDocument();

    const byStatus = screen.getByRole('region', { name: 'Por status' });
    expect(within(byStatus).getByText('Cancelado').closest('li')).toHaveTextContent('2');
    expect(screen.getByText(/America\/Sao_Paulo/)).toBeInTheDocument();
  });

  it('lists the products with low stock, sold out first', async () => {
    await setup();
    summaryRequest().flush(aDashboard());
    stockRequest().flush({ items: stock, nextCursor: null });

    const lowStock = await screen.findByRole('region', { name: 'Estoque baixo' });
    const items = within(lowStock).getAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('Adesivos');
    expect(items[0]).toHaveTextContent('Esgotado');
    expect(items[1]).toHaveTextContent('3 disponíveis');
    expect(within(lowStock).queryByText('Caneca')).not.toBeInTheDocument();
  });

  it('says when no product is low or when the stock cannot be loaded', async () => {
    await setup();
    summaryRequest().flush(aDashboard());
    stockRequest().flush({ items: [stock[0]], nextCursor: null });
    expect(await screen.findByText(/Nenhum produto com 10 unidades ou menos/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Atualizar' }));
    summaryRequest().flush(aDashboard());
    stockRequest().flush(null, { status: 500, statusText: 'Error' });
    expect(await screen.findByText('Não foi possível carregar o estoque.')).toBeInTheDocument();
  });

  it('changes the period, shows zero revenue and reports errors', async () => {
    await setup();
    summaryRequest().flush(aDashboard({ confirmedRevenue: [], daily: [] }));
    stockRequest().flush({ items: [], nextCursor: null });
    expect(await screen.findByText(/R\$\s*0,00/)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Período'), { target: { value: '30' } });

    summaryRequest(30).flush(null, { status: 500, statusText: 'Error' });
    stockRequest().flush({ items: [], nextCursor: null });
    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('servidor'));
  });

  it('labels only some days in long periods', async () => {
    await setup();
    const daily = Array.from({ length: 30 }, (_, index) => ({
      date: `2026-11-${String(index + 1).padStart(2, '0')}`,
      orders: index,
      cancelled: 0,
    }));
    summaryRequest().flush(aDashboard({ daily }));
    stockRequest().flush({ items: [], nextCursor: null });

    const perDay = await screen.findByRole('list', { name: 'Pedidos e cancelados por dia' });
    expect(within(perDay).getAllByRole('listitem')).toHaveLength(30);
    expect(screen.getByText('hoje')).toBeInTheDocument();
  });

  it('uses the browser time zone, falling back to UTC', () => {
    const spy = jest
      .spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions')
      .mockReturnValue({ timeZone: '' } as Intl.ResolvedDateTimeFormatOptions);

    expect(browserTimeZone()).toBe('UTC');
    spy.mockRestore();
  });
});
