import { test, expect } from '../../support/fixtures';
import { criarOferta } from '../../support/api';
import { conferirCabecalhosDeSeguranca, violacoesDaCsp } from '../../support/cabecalhos';

/** Cabeçalhos de segurança do front-app (#202). */
test.describe('Cabeçalhos de segurança (front-app)', () => {
  test('páginas e rotas de sessão mandam CSP, anti-clickjacking e sem X-Powered-By', async ({ request }) => {
    for (const caminho of ['/', '/entrar', '/dashboard', '/produtos']) {
      conferirCabecalhosDeSeguranca(await request.get(caminho));
    }
    conferirCabecalhosDeSeguranca(await request.post('/api/sessao/renovar'));
  });

  test('telas do app abrem sem violar a CSP', async ({ page, produtor, baseURL }) => {
    const violacoes = violacoesDaCsp(page);
    await criarOferta(produtor);

    // Login pela tela (fontes do Google, /api/sessao, API) e as telas com foto e dados da API
    await page.goto('/entrar');
    await page.getByLabel('E-mail').fill(produtor.email);
    await page.getByLabel(/^Senha/).fill(produtor.senha);
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page).toHaveURL(/\/dashboard$/);

    const nav = page.getByRole('navigation', { name: 'Navegação principal' });
    await nav.getByRole('link', { name: 'Produtos' }).click();
    await expect(page.getByRole('img').first()).toBeVisible();
    await nav.getByRole('link', { name: 'Pedidos' }).click();
    await expect(page).toHaveURL(/\/pedidos$/);
    await page.goto(new URL('/perfil', baseURL).toString());
    await expect(nav).toBeVisible();

    expect(violacoes).toEqual([]);
  });
});
