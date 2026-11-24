import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

const WCAG_22_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

test.describe('home page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
  });

  test('has a descriptive title and a level-one heading', async ({ page }) => {
    await expect(page).toHaveTitle('Início · OrderFlow');
    await expect(page.getByRole('heading', { level: 1 })).toHaveText('OrderFlow');
  });

  for (const colorScheme of ['light', 'dark'] as const) {
    test(`has no WCAG 2.2 AA violations in the ${colorScheme} theme`, async ({ page }) => {
      await page.emulateMedia({ colorScheme });

      const results = await new AxeBuilder({ page }).withTags(WCAG_22_AA).analyze();

      expect(results.violations).toEqual([]);
    });
  }

  test('lets keyboard users skip straight to the main content', async ({ page }) => {
    await page.keyboard.press('Tab');
    const skipLink = page.getByRole('link', { name: 'Pular para o conteúdo' });
    await expect(skipLink).toBeFocused();

    await page.keyboard.press('Enter');

    await expect(page.getByRole('main')).toBeFocused();
  });
});
