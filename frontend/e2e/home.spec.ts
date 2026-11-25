import { expect, test } from '@playwright/test';
import { expectNoA11yViolations } from './support/a11y';

test.describe('home page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // O bootstrap espera o /config.json: só interage depois que a página está pronta.
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  });

  test('has a descriptive title, a level-one heading and a sign-in button', async ({ page }) => {
    await expect(page).toHaveTitle('Início · OrderFlow');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('OrderFlow');
    await expect(page.getByRole('button', { name: 'Entrar' }).first()).toBeVisible();
  });

  test('has no WCAG 2.2 AA violations in the light and dark themes', async ({ page }) => {
    await expectNoA11yViolations(page);
  });

  test('explains when the login service is down and lets the user retry', async ({ page }) => {
    await page.route('**/.well-known/openid-configuration', (route) => route.abort());

    await page.getByRole('button', { name: 'Entrar' }).first().click();

    const alert = page.getByRole('alert');
    await expect(alert).toContainText('Não foi possível conectar ao serviço de login');
    await expect(alert.getByRole('button', { name: 'Tentar de novo' })).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('lets keyboard users skip straight to the main content', async ({ page }) => {
    await page.keyboard.press('Tab');
    const skipLink = page.getByRole('link', { name: 'Pular para o conteúdo' });
    await expect(skipLink).toBeFocused();

    await page.keyboard.press('Enter');

    await expect(page.getByRole('main')).toBeFocused();
  });
});
