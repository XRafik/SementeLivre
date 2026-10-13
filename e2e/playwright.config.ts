import { defineConfig, devices } from '@playwright/test';

/**
 * Testes web de ponta a ponta: front-app (proprietário, :3000) e front-site
 * (catálogo público, :3001) contra o backend real (:8080, Postgres de verdade).
 *
 * O backend precisa estar no ar antes (ver e2e/README.md); os dois frontends
 * sobem aqui com `next start`, então rode `npm run build` neles antes.
 */

const CI = !!process.env.CI;
const APP_URL = process.env.APP_URL ?? 'http://localhost:3000';
const SITE_URL = process.env.SITE_URL ?? 'http://localhost:3001';

export default defineConfig({
  testDir: './tests',
  globalSetup: './support/global-setup.ts',
  fullyParallel: true,
  forbidOnly: CI,
  retries: CI ? 1 : 0,
  workers: CI ? 2 : undefined,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  reporter: CI
    ? [['list'], ['html', { open: 'never' }], ['junit', { outputFile: 'test-results/junit.xml' }]]
    : [['list'], ['html', { open: 'never' }]],
  use: {
    ...devices['Pixel 7'],
    // O front-app é mobile-first; Pixel 7 roda no Chromium, o único browser instalado
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    { name: 'front-app', testDir: './tests/app', use: { baseURL: APP_URL } },
    { name: 'front-site', testDir: './tests/site', use: { baseURL: SITE_URL, ...devices['Desktop Chrome'] } },
  ],
  webServer: [
    {
      command: 'npm --prefix ../front-app run start',
      url: APP_URL,
      reuseExistingServer: !CI,
      timeout: 120_000,
    },
    {
      command: 'npm --prefix ../front-site run start',
      url: SITE_URL,
      reuseExistingServer: !CI,
      timeout: 120_000,
    },
  ],
});
