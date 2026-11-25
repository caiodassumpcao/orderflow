import AxeBuilder from '@axe-core/playwright';
import { Page, expect } from '@playwright/test';

export const WCAG_22_AA = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

/**
 * Espera a interface assentar antes de analisar: transições terminadas (opacidade parcial engana o
 * cálculo de contraste) e snackbar já anunciado (o Material deixa o conteúdo num contêiner
 * aria-hidden por ~150 ms antes de movê-lo para a região live).
 */
async function settle(page: Page): Promise<void> {
  await page.waitForFunction(
    () =>
      !document.querySelector('.mat-mdc-snack-bar-label > [aria-hidden="true"]') &&
      document.getAnimations().every(
        (animation) =>
          animation.playState !== 'running' ||
          // Indicadores de carregamento giram para sempre; só as transições finitas importam.
          animation.effect?.getComputedTiming().iterations === Infinity,
      ),
  );
}

/** Roda o axe (WCAG 2.2 AA) na página atual, nos temas claro e escuro. */
export async function expectNoA11yViolations(page: Page, include?: string): Promise<void> {
  for (const colorScheme of ['light', 'dark'] as const) {
    await page.emulateMedia({ colorScheme });
    await settle(page);
    let builder = new AxeBuilder({ page }).withTags(WCAG_22_AA);
    if (include) {
      builder = builder.include(include);
    }
    const { violations } = await builder.analyze();
    const summary = violations.map(
      (v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(' | ')}`,
    );
    expect(summary, `violações de acessibilidade no tema ${colorScheme}`).toEqual([]);
  }
  await page.emulateMedia({ colorScheme: null });
}
