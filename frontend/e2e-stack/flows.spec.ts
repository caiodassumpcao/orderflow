import { expect, test } from '@playwright/test';
import { expectNoA11yViolations } from '../e2e/support/a11y';
import { devAdmin, devUser, placeOrder, signIn, signOut } from './support';

/**
 * Fluxos críticos ponta a ponta, sem simulação: login no Keycloak, comando na API, evento no Kafka,
 * saga com o inventário, projeção no modelo de leitura e a imagem nginx de produção servindo a SPA.
 */
test.describe('stack real', () => {
  test('o cliente compra, a saga confirma e a operação envia', async ({ page }) => {
    let orderId = '';

    await test.step('cliente cria um pedido ao preço de catálogo', async () => {
      await signIn(page, '/pedidos', devUser());
      orderId = await placeOrder(page, 'SKU-001', 1);
      await expect(page.getByText('Pedido criado. Acompanhe a reserva de estoque.')).toBeVisible();
    });

    await test.step('a saga reserva o estoque e confirma o pedido', async () => {
      await expect(page.getByText('Confirmado').first()).toBeVisible({ timeout: 30_000 });
      await expectNoA11yViolations(page);
      await page
        .getByRole('navigation', { name: 'Trilha' })
        .getByRole('link', { name: 'Meus pedidos' })
        .click();
      await expect(page.getByRole('link', { name: `Pedido ${orderId.slice(0, 8)}` })).toBeVisible();
      await signOut(page);
    });

    await test.step('a operação envia o pedido', async () => {
      await signIn(page, `/admin/pedidos/${orderId}`, devAdmin());
      await expect(page.getByText('Cliente:')).toBeVisible();
      await page.getByRole('button', { name: 'Marcar como enviado' }).click();
      await page.getByRole('dialog').getByRole('button', { name: 'Marcar como enviado' }).click();
      await expect(page.getByText('Pedido marcado como enviado.')).toBeVisible();
      await expect(page.getByText('Enviado').first()).toBeVisible();
    });

    await test.step('o painel reflete os pedidos', async () => {
      await page.goto('/admin');
      await expect(page.getByRole('list', { name: 'Indicadores' })).toBeVisible();
      await expectNoA11yViolations(page);
    });
  });

  test('o inventário recusa um pedido sem estoque e o cliente vê o motivo', async ({ page }) => {
    await signIn(page, '/pedidos', devUser());

    await placeOrder(page, 'SKU-005', 1000);

    await expect(page.getByText(/Estoque insuficiente \(SKU-005\)/)).toBeVisible({
      timeout: 30_000,
    });
    await expect(page.getByText('Cancelado').first()).toBeVisible();
  });

  test('a operação cadastra um produto, repõe o estoque e o cliente o vê no catálogo', async ({
    page,
  }) => {
    const sku = `SKU-E2E-${Date.now().toString(36).toUpperCase()}`;
    await signIn(page, '/admin/estoque', devAdmin());

    await page.getByLabel('SKU').fill(sku);
    await page.getByLabel('Nome').fill(`Produto ${sku}`);
    await page.getByLabel('Preço unitário').fill('12.34');
    await page.getByLabel('Estoque inicial').fill('0');
    await page.getByRole('button', { name: 'Cadastrar produto' }).click();
    await expect(page.getByText(`Produto Produto ${sku} cadastrado.`)).toBeVisible();

    // A primeira página lista 50 produtos em ordem de SKU; carrega até achar o novo.
    const restock = page.getByRole('button', { name: `Repor Produto ${sku}` });
    const loadMore = page.getByRole('button', { name: 'Carregar mais' });
    // A lista recarrega depois do cadastro: espera a linha nova ou o botão da próxima página.
    await expect(restock.or(loadMore)).toBeVisible();
    while (!(await restock.isVisible())) {
      await loadMore.click();
      await expect(restock.or(loadMore)).toBeVisible();
    }
    await restock.click();
    await page.getByRole('dialog').getByLabel('Unidades a repor').fill('7');
    await page.getByRole('dialog').getByRole('button', { name: 'Repor' }).click();
    await expect(
      page.getByText(`Estoque de Produto ${sku} atualizado: 7 disponíveis.`),
    ).toBeVisible();
    await signOut(page);

    await signIn(page, '/pedidos/novo', devUser());
    await expect(
      page.getByRole('option', { name: new RegExp(`Produto ${sku}.*12,34`) }),
    ).toBeEnabled();
  });
});
