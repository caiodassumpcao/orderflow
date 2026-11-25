import { defineConfig, devices } from '@playwright/test';
import { existsSync, readFileSync } from 'node:fs';
import { resolve } from 'node:path';

/**
 * E2E contra a stack real: Keycloak, API (Postgres + Kafka + saga) e a SPA já no ar em
 * http://localhost:4200 (dev server ou imagem nginx). Nada é simulado. Localmente: `make up`,
 * `make backend-run`, a SPA na 4200 e `make test-e2e-stack`; no CI, o job `e2e-stack` sobe tudo.
 * As senhas dos usuários de dev vêm do ambiente ou do .env da raiz.
 */
function loadRootEnv(): void {
  const file = resolve(__dirname, '..', '.env');
  if (!existsSync(file)) {
    return;
  }
  for (const line of readFileSync(file, 'utf8').split(/\r?\n/)) {
    const match = /^([A-Z0-9_]+)=(.*)$/.exec(line.trim());
    if (match && process.env[match[1]] === undefined) {
      process.env[match[1]] = match[2];
    }
  }
}
loadRootEnv();

const isCI = !!process.env['CI'];

export default defineConfig({
  testDir: './e2e-stack',
  // Os testes compartilham banco, estoque e Keycloak: um de cada vez.
  fullyParallel: false,
  workers: 1,
  forbidOnly: isCI,
  retries: isCI ? 1 : 0,
  timeout: 60_000,
  expect: { timeout: 15_000 },
  reporter: isCI
    ? [['github'], ['html', { open: 'never', outputFolder: 'playwright-report-stack' }]]
    : [['list']],
  use: {
    baseURL: process.env['E2E_BASE_URL'] ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
});
