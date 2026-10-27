import { test, expect, toast } from '../../support/fixtures';
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

  test('token inválido sem refresh válido encerra a sessão', async ({ page }) => {
    await page.goto('/entrar');
    await page.evaluate(() => {
      localStorage.setItem('sl_access_token', 'token-invalido');
      localStorage.setItem('sl_refresh_token', 'refresh-invalido');
    });
    await page.goto('/dashboard');

    await expect(page).toHaveURL(/\/entrar$/);
    expect(await page.evaluate(() => localStorage.getItem('sl_access_token'))).toBeNull();
  });

  test('access token vencido é renovado pelo refresh sem pedir login', async ({ page, produtor }) => {
    await page.goto('/entrar');
    await page.evaluate((refresh) => {
      localStorage.setItem('sl_access_token', 'token-vencido');
      localStorage.setItem('sl_refresh_token', refresh);
    }, produtor.refreshToken);
    await page.goto('/pedidos');

    await expect(page).toHaveURL(/\/pedidos$/);
    await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('sl_access_token'))).not.toBe('token-vencido');
  });

  test('backend fora do ar ao abrir o app mantém a sessão e oferece tentar de novo', async ({ appLogado: page }) => {
    await page.context().route(`${API_URL}/**`, (route) => route.abort('internetdisconnected'));
    await page.reload();

    await expect(page.getByText('Não foi possível falar com o servidor.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Tentar novamente' })).toBeVisible();
    // Não desloga: os tokens continuam salvos
    expect(await page.evaluate(() => localStorage.getItem('sl_access_token'))).toBeTruthy();

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
    await page.goto('/entrar');
    await page.getByLabel('E-mail').fill('alguem@sementelivre.test');
    await page.getByLabel(/^Senha/).fill('Senha123');
    await page.getByRole('button', { name: 'Entrar' }).click();

    await expect(toast(page, /Sem conexão com o servidor/)).toBeVisible();
    await expect(page).toHaveURL(/\/entrar$/);
  });
});
