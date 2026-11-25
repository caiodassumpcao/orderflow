import { expect, test } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';
import { FakeApi } from './support/fake-api';
import { CUSTOMER, useFakeIdentityProvider } from './support/fake-idp';

test.describe('customer', () => {
  let api: FakeApi;

  test.beforeEach(async ({ context }) => {
    api = new FakeApi();
    api.seedOrder(CUSTOMER.sub, 'SHIPPED', 'SKU-002', 1);
    await useFakeIdentityProvider(context, CUSTOMER);
    await api.install(context);
  });

  test('signs in through PKCE and comes back to the page they asked for', async ({ page }) => {
    await page.goto('/pedidos');

    await expect(page).toHaveURL(/\/pedidos$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Meus pedidos' })).toBeVisible();
    await expect(page.getByText('Ana Cliente')).toBeVisible();
    await expect(page.getByRole('table').getByText('Enviado')).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('places an order at the catalog price and follows the stock saga', async ({ page }) => {
    await page.goto('/pedidos/novo');
    await expect(page.getByRole('heading', { level: 1, name: 'Novo pedido' })).toBeVisible();
    await expect(page.getByRole('option', { name: /Kit de adesivos.*sem estoque/ })).toBeDisabled();

    await page.getByLabel('Produto').selectOption('SKU-001');
    await page.getByLabel('Quantidade').fill('3');
    await expect(page.getByText(/Total do pedido:\s*R\$\s*59,70/)).toBeVisible();
    await expectNoA11yViolations(page);

    await page.getByRole('button', { name: 'Criar pedido' }).click();

    await expect(page).toHaveURL(/\/pedidos\/0193e2e0-/);
    await expect(page.getByText('Pedido criado. Acompanhe a reserva de estoque.')).toBeVisible();
    await expect(page.getByText('Aguardando estoque').first()).toBeVisible();
    await expect(page.getByText('Confirmado').first()).toBeVisible({ timeout: 15_000 });
    await expect(page.getByRole('button', { name: 'Cancelar pedido' })).toHaveCount(0);
    await expectNoA11yViolations(page);

    await page.getByRole('link', { name: '← Meus pedidos' }).click();
    await expect(page.getByRole('table').getByRole('link')).toHaveCount(2);
  });

  test('cancels an order that still waits for stock', async ({ page }) => {
    const id = api.seedOrder(CUSTOMER.sub, 'CREATED');
    await page.goto(`/pedidos/${id}`);

    await page.getByRole('button', { name: 'Cancelar pedido' }).click();
    const dialog = page.getByRole('dialog', { name: 'Cancelar pedido?' });
    await expect(dialog).toBeVisible();
    await expectNoA11yViolations(page);
    await dialog.getByLabel('Motivo (opcional)').fill('Comprei errado');
    await dialog.getByRole('button', { name: 'Cancelar pedido' }).click();

    await expect(page.getByText('Pedido cancelado.')).toBeVisible();
    await expect(page.getByText('Comprei errado')).toBeVisible();
  });

  test('shows a calm loading indicator when the system asks for reduced motion', async ({
    page,
  }) => {
    // Animações desligadas no sistema operacional: a barra animada não pode piscar.
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const id = api.seedOrder(CUSTOMER.sub, 'CREATED');
    await page.goto(`/pedidos/${id}`);

    await expect(page.getByText('Aguardando a reserva de estoque…', { exact: true })).toBeVisible();
    await expect(page.getByRole('progressbar')).toBeHidden();
    const loopingAnimations = await page.evaluate(
      () =>
        document
          .getAnimations()
          .filter(
            (a) =>
              a.playState === 'running' && a.effect?.getComputedTiming().iterations === Infinity,
          ).length,
    );
    expect(loopingAnimations).toBe(0);
    await expectNoA11yViolations(page);
  });

  test('is kept away from the operation pages', async ({ page }) => {
    await page.goto('/pedidos');
    await expect(page.getByRole('navigation', { name: 'Principal' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Estoque' })).toHaveCount(0);

    await page.goto('/admin/estoque');

    await expect(page.getByRole('heading', { level: 1, name: 'Acesso negado' })).toBeVisible();
  });

  test('signs out and returns to the home page', async ({ page }) => {
    await page.goto('/pedidos');
    await expect(page.getByText('Ana Cliente')).toBeVisible();

    await page.getByRole('button', { name: 'Sair' }).click();

    await expect(page.getByRole('heading', { level: 1, name: 'OrderFlow' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Entrar' }).first()).toBeVisible();
  });
});
