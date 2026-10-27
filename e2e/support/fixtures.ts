import { Page, test as base, expect } from '@playwright/test';
import { criarProdutor, Produtor } from './api';

/**
 * - `produtor`: conta nova, criada no backend real para cada teste.
 * - `appLogado`: página do front-app já com a sessão desse produtor (tokens no
 *   localStorage, como o próprio login grava), para não repetir o login na UI.
 */
type Fixtures = {
  produtor: Produtor;
  appLogado: Page;
};

export const test = base.extend<Fixtures>({
  produtor: async ({}, use) => {
    await use(await criarProdutor());
  },
  appLogado: async ({ page, produtor }, use) => {
    await page.goto('/entrar');
    await page.evaluate(([access, refresh]) => {
      localStorage.setItem('sl_access_token', access);
      localStorage.setItem('sl_refresh_token', refresh);
    }, [produtor.accessToken, produtor.refreshToken]);
    await page.goto('/dashboard');
    await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();
    await use(page);
  },
});

export { expect };

/** Toast do front-app (role=status para sucesso/aviso, alert para erro). */
export function toast(page: Page, texto: string | RegExp) {
  return page.getByRole('region', { name: 'Notificações' }).getByText(texto);
}
