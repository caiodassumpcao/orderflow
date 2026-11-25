import { TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { MatDialog } from '@angular/material/dialog';
import { fireEvent, screen, waitFor } from '@testing-library/angular';
import { firstValueFrom } from 'rxjs';
import { StockItem } from '../../../core/api/models';
import { API, aStockItem, problem, testProviders } from '../../../../testing/test-support';
import { RestockDialog } from './restock-dialog';

describe('RestockDialog', () => {
  let http: HttpTestingController;

  async function open(item: StockItem) {
    TestBed.configureTestingModule({ providers: testProviders() });
    http = TestBed.inject(HttpTestingController);
    const ref = TestBed.inject(MatDialog).open<RestockDialog, StockItem, StockItem>(RestockDialog, {
      data: item,
    });
    await firstValueFrom(ref.afterOpened());
    return firstValueFrom(ref.afterClosed());
  }

  const restockUrl = `${API}/api/v1/inventory/SKU-001/restock`;
  const typeQuantity = (value: string) =>
    fireEvent.input(screen.getByLabelText('Unidades a repor'), { target: { value } });
  const submit = () => fireEvent.click(screen.getByRole('button', { name: 'Repor' }));

  afterEach(() => {
    TestBed.inject(MatDialog).closeAll();
    http.verify();
  });

  it('closes with the restocked product', async () => {
    const closed = open(aStockItem({ version: 3 }));
    await screen.findByRole('dialog', { name: /Repor Caneca/ });

    typeQuantity('10');
    submit();
    (await waitFor(() => http.expectOne(restockUrl))).flush(
      aStockItem({ available: 20, version: 4 }),
    );

    await expect(closed).resolves.toEqual(expect.objectContaining({ available: 20, version: 4 }));
  });

  it('reloads the product when it changed and lets the operator confirm again', async () => {
    const closed = open(aStockItem({ available: 10, version: 3 }));
    await screen.findByRole('dialog');
    typeQuantity('5');
    submit();

    (await waitFor(() => http.expectOne(restockUrl))).flush(problem('stock-item-modified', 412), {
      status: 412,
      statusText: 'Precondition Failed',
    });
    expect(await screen.findByRole('alert')).toHaveTextContent('O produto mudou');
    http
      .expectOne(`${API}/api/v1/inventory/SKU-001`)
      .flush(aStockItem({ available: 7, version: 5 }));
    expect(await screen.findByText('7')).toBeInTheDocument();

    submit();
    const retry = await waitFor(() => http.expectOne(restockUrl));
    expect(retry.request.headers.get('If-Match')).toBe('"5"');
    retry.flush(aStockItem({ available: 12, version: 6 }));
    await expect(closed).resolves.toEqual(expect.objectContaining({ version: 6 }));
  });

  it('validates the quantity and can be cancelled', async () => {
    const closed = open(aStockItem());
    await screen.findByRole('dialog');

    typeQuantity('0');
    submit();
    expect(await screen.findByText(/número inteiro de 1 a/)).toBeInTheDocument();
    http.expectNone(restockUrl);

    fireEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    await expect(closed).resolves.toBeUndefined();
  });

  it('shows other errors without reloading', async () => {
    open(aStockItem());
    await screen.findByRole('dialog');
    typeQuantity('1');
    submit();

    (await waitFor(() => http.expectOne(restockUrl))).flush(problem('stock-limit-exceeded', 422), {
      status: 422,
      statusText: 'Unprocessable',
    });

    expect(await screen.findByRole('alert')).toHaveTextContent('limite de unidades');
    http.expectNone(`${API}/api/v1/inventory/SKU-001`);
  });
});
