import { test, expect, toast, gravarSessao, cookieDeSessao } from '../../support/fixtures';
import { API_URL } from '../../support/api';

/**
 * Comportamento offline e de sessão do front-app.
 * "Offline" aqui é o backend inacessível: as requisições para a API são
 * abortadas no navegador, os dados continuam vindo do backend real.
 */
test.describe('Sessão e offline (front-app)', () => {
  test('rota interna sem sessão redireciona para o login', async ({ page }) => {
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/entrar$/);
  });

  test('usuário logado que abre /entrar vai direto ao dashboard', async ({ appLogado: page }) => {
    await page.goto('/entrar');
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('sessão continua após recarregar a página', async ({ appLogado: page }) => {
    await page.reload();
    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();
  });

  test('refresh inválido no cookie encerra a sessão e apaga o cookie', async ({ page, baseURL }) => {
    await gravarSessao(page, baseURL!, 'refresh-invalido');
    await page.goto('/dashboard');

    await expect(page).toHaveURL(/\/entrar$/);
    expect(await cookieDeSessao(page)).toBeUndefined();
  });

  test('página aberta do zero renova a sessão pelo cookie sem pedir login', async ({ page, produtor, baseURL }) => {
    await gravarSessao(page, baseURL!, produtor.refreshToken);
    await page.goto('/pedidos');

    await expect(page).toHaveURL(/\/pedidos$/);
    await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();
    // O refresh troca a cada uso
    expect((await cookieDeSessao(page))?.value).not.toBe(produtor.refreshToken);
  });

  test('tokens ficam fora do alcance do JavaScript da página', async ({ appLogado: page }) => {
    const sessao = await cookieDeSessao(page);
    expect(sessao?.httpOnly).toBe(true);
    expect(sessao?.sameSite).toBe('Strict');
    expect(sessao?.path).toBe('/api/sessao');

    // O refresh token (7 dias) não aparece para script nenhum; o access (30 min) fica na aba
    const visivelNoJs = await page.evaluate(() => ({
      cookies: document.cookie,
      local: Object.keys(localStorage).filter((k) => k.includes('token') || k.includes('refresh')),
      daAba: Object.keys(sessionStorage),
    }));
    expect(visivelNoJs.cookies).not.toContain('sl_refresh');
    expect(visivelNoJs.local).toEqual([]);
    expect(visivelNoJs.daAba).toEqual(['sl_access']);
  });

  test('navegar e recarregar não renovam a sessão a cada página', async ({ appLogado: page }) => {
    const renovacoes: string[] = [];
    page.on('request', (req) => {
      if (req.url().includes('/api/sessao/renovar')) renovacoes.push(req.url());
    });

    await page.goto('/produtos');
    await page.goto('/pedidos');
    await page.reload();
    await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();

    expect(renovacoes).toEqual([]);
  });

  test('várias abas abertas juntas não derrubam a sessão', async ({ appLogado: page }) => {
    const abas = await Promise.all([1, 2, 3].map(() => page.context().newPage()));
    await Promise.all(abas.map((aba) => aba.goto('/pedidos')));

    for (const aba of abas) {
      await expect(aba.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();
    }
    await page.reload();
    await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();
  });

  test('backend fora do ar ao abrir o app mantém a sessão e oferece tentar de novo', async ({ appLogado: page }) => {
    await page.context().route(`${API_URL}/**`, (route) => route.abort('internetdisconnected'));
    await page.reload();

    await expect(page.getByText('Não foi possível falar com o servidor.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
    // Não desloga: a sessão continua no cookie
    expect(await cookieDeSessao(page)).toBeDefined();

    // Backend volta: "Tentar novamente" recupera a sessão
    await page.context().unroute(`${API_URL}/**`);
    await page.getByRole('button', { name: 'Tentar novamente' }).click();
    await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();
  });

  test('perda de conexão durante o uso mostra aviso de sem conexão', async ({ appLogado: page }) => {
    await page.context().route(`${API_URL}/**`, (route) => route.abort('internetdisconnected'));
    await page.getByRole('navigation', { name: 'Navegação principal' }).getByRole('link', { name: 'Pedidos' }).click();

    await expect(toast(page, /Sem conexão com o servidor/)).toBeVisible();
  });

  test('login sem backend avisa que não há conexão', async ({ page }) => {
    await page.context().route(`${API_URL}/**`, (route) => route.abort('internetdisconnected'));
    // O login passa pelo servidor do app (/api/sessao)
    await page.context().route('**/api/sessao**', (route) => route.abort('internetdisconnected'));
    await page.goto('/entrar');
    await page.getByLabel('E-mail').fill('alguem@sementelivre.test');
    await page.getByLabel(/^Senha/).fill('Senha123');
    await page.getByRole('button', { name: 'Entrar' }).click();

    await expect(toast(page, /Sem conexão com o servidor/)).toBeVisible();
    await expect(page).toHaveURL(/\/entrar$/);
  });
});
