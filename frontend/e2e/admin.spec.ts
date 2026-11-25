import { expect, test } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { FakeApi } from './support/fake-api';
import { ADMIN, CUSTOMER, useFakeIdentityProvider } from './support/fake-idp';

test.describe('operation', () => {
  let api: FakeApi;
  let confirmedId: string;

  test.beforeEach(async ({ context }) => {
    api = new FakeApi();
    confirmedId = api.seedOrder(CUSTOMER.sub, 'CONFIRMED', 'SKU-001', 2);
    api.seedOrder('another-customer', 'SHIPPED', 'SKU-002', 1);
    api.seedOrder(CUSTOMER.sub, 'CANCELLED');
    await useFakeIdentityProvider(context, ADMIN);
    await api.install(context);
  });

  test('sees the dashboard with orders by status and per day', async ({ page }) => {
    await page.goto('/admin');

    await expect(page.getByRole('heading', { level: 1, name: 'Painel da operação' })).toBeVisible();
    const indicators = page.getByRole('list', { name: 'Indicadores' });
    await expect(indicators.getByText('3', { exact: true })).toBeVisible();
    await expect(indicators.getByText(/R\$\s*139,80/)).toBeVisible();
    await expect(page.getByRole('region', { name: /últimos 7 dias/ }).getByRole('row')).toHaveCount(
      8,
    );
    await expectNoA11yViolations(page);
  });

  test('ships a confirmed order of any customer', async ({ page }) => {
    await page.goto('/admin/pedidos');
    await expect(page.getByRole('cell', { name: 'another-customer' })).toBeVisible();
    await expectNoA11yViolations(page);

    await page.getByLabel('Status').selectOption('CONFIRMED');
    await page.getByRole('button', { name: 'Filtrar' }).click();
    await expect(page.getByRole('table').getByRole('link')).toHaveCount(1);
    await page.getByRole('link', { name: `Pedido ${confirmedId.slice(0, 8)}` }).click();

    await expect(page.getByText(CUSTOMER.sub)).toBeVisible();
    await page.getByRole('button', { name: 'Marcar como enviado' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Marcar como enviado' }).click();

    await expect(page.getByText('Pedido marcado como enviado.')).toBeVisible();
    await expect(page.getByText('Enviado').first()).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('restocks a sold out product and registers a new one', async ({ page }) => {
    await page.goto('/admin/estoque');
    await expect(page.getByText('(esgotado)')).toBeVisible();
    await expectNoA11yViolations(page);

    await page.getByRole('button', { name: 'Repor Kit de adesivos' }).click();
    const dialog = page.getByRole('dialog', { name: 'Repor Kit de adesivos' });
    await dialog.getByLabel('Unidades a repor').fill('25');
    await expectNoA11yViolations(page);
    await dialog.getByRole('button', { name: 'Repor' }).click();
    await expect(
      page.getByText('Estoque de Kit de adesivos atualizado: 25 disponíveis.'),
    ).toBeVisible();
    await expect(page.getByText('(esgotado)')).toHaveCount(0);

    await page.getByLabel('SKU').fill('SKU-006');
    await page.getByLabel('Nome').fill('Garrafa térmica');
    await page.getByLabel('Preço unitário').fill('89.90');
    await page.getByLabel('Estoque inicial').fill('40');
    await page.getByRole('button', { name: 'Cadastrar produto' }).click();

    await expect(page.getByText('Produto Garrafa térmica cadastrado.')).toBeVisible();
    await expect(page.getByRole('rowheader', { name: 'Garrafa térmica' })).toBeVisible();
    await expectNoA11yViolations(page);
  });
});
