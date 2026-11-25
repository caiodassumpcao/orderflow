import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { fireEvent, render, screen, within } from '@testing-library/angular';
import { API, aDashboard, testProviders } from '../../../../testing/test-support';
import { DASHBOARD_DAYS, Dashboard, browserTimeZone } from './dashboard';

describe('Dashboard', () => {
  let http: HttpTestingController;

  async function setup() {
    await render(Dashboard, { providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
  }

  const summaryRequest = () =>
    http.expectOne(
      (r) =>
        r.url === `${API}/api/v1/dashboard/summary` &&
        r.params.get('days') === String(DASHBOARD_DAYS) &&
        r.params.get('timeZone') === browserTimeZone(),
    );

  afterEach(() => http.verify());

  it('shows the indicators, the orders by status and per day', async () => {
    await setup();
    summaryRequest().flush(aDashboard());

    const indicators = await screen.findByRole('list', { name: 'Indicadores' });
    expect(within(indicators).getByText('9')).toBeInTheDocument();
    expect(within(indicators).getByText(/R\$\s*150,50/)).toBeInTheDocument();
    // Aguardando estoque = CREATED + STOCK_RESERVED
    expect(within(indicators).getByText('2')).toBeInTheDocument();
    const byStatus = screen.getByRole('region', { name: 'Pedidos por status' });
    expect(within(byStatus).getByRole('rowheader', { name: 'Cancelado' })).toBeInTheDocument();
    const byDay = screen.getByRole('region', { name: /últimos 7 dias/ });
    expect(within(byDay).getAllByRole('row')).toHaveLength(3);
    expect(screen.getByText(/America\/Sao_Paulo/)).toBeInTheDocument();
  });

  it('shows zero revenue when nothing was confirmed and refreshes on demand', async () => {
    await setup();
    summaryRequest().flush(aDashboard({ confirmedRevenue: [], daily: [] }));
    expect(await screen.findByText(/R\$\s*0,00/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Atualizar' }));

    summaryRequest().flush(null, { status: 500, statusText: 'Error' });
    expect(await screen.findByRole('alert')).toHaveTextContent('servidor');
  });

  it('uses the browser time zone, falling back to UTC', () => {
    const spy = jest
      .spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions')
      .mockReturnValue({ timeZone: '' } as Intl.ResolvedDateTimeFormatOptions);

    expect(browserTimeZone()).toBe('UTC');
    spy.mockRestore();
  });
});
