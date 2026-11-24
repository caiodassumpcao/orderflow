import { defineConfig, devices } from '@playwright/test';

const isCI = !!process.env['CI'];
const port = 4200;
// Com PLAYWRIGHT_BASE_URL (ex.: a imagem nginx), os testes rodam contra ela sem subir o dev server
const externalBaseUrl = process.env['PLAYWRIGHT_BASE_URL'];

export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: isCI,
  retries: isCI ? 2 : 0,
  workers: isCI ? 1 : undefined,
  reporter: isCI ? [['github'], ['html', { open: 'never' }]] : [['list']],
  use: {
    baseURL: externalBaseUrl ?? `http://localhost:${port}`,
    trace: 'on-first-retry',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: externalBaseUrl
    ? undefined
    : {
        command: `npx ng serve --port ${port}`,
        url: `http://localhost:${port}`,
        reuseExistingServer: !isCI,
        timeout: 120_000,
      },
});
