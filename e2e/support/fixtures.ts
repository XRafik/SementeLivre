import { Page, test as base, expect } from '@playwright/test';
import { criarProdutor, Produtor } from './api';

/**
 * - `produtor`: conta nova, criada no backend real para cada teste.
 * - `appLogado`: página do front-app já com a sessão desse produtor (refresh token
 *   no cookie de sessão, como o próprio login grava), para não repetir o login na UI.
 */
type Fixtures = {
  produtor: Produtor;
  appLogado: Page;
};

export const test = base.extend<Fixtures>({
  produtor: async ({}, use) => {
    await use(await criarProdutor());
  },
  appLogado: async ({ page, produtor, baseURL }, use) => {
    await gravarSessao(page, baseURL!, produtor.refreshToken);
    await page.goto('/dashboard');
    await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();
    await use(page);
  },
});

export { expect };

export const COOKIE_DE_SESSAO = 'sl_refresh';

/** Grava o refresh token no cookie de sessão do front-app, como o login faz (#201). */
export async function gravarSessao(page: Page, baseURL: string, refreshToken: string) {
  await page.context().addCookies([{
    name: COOKIE_DE_SESSAO,
    value: refreshToken,
    domain: new URL(baseURL).hostname,
    path: '/api/sessao',
    httpOnly: true,
    sameSite: 'Strict',
  }]);
}

export async function cookieDeSessao(page: Page) {
  return (await page.context().cookies()).find((c) => c.name === COOKIE_DE_SESSAO);
}

/** Toast do front-app (role=status para sucesso/aviso, alert para erro). */
export function toast(page: Page, texto: string | RegExp) {
  return page.getByRole('region', { name: 'Notificações' }).getByText(texto);
}
