import { test, expect } from '@playwright/test';
import { criarOferta, criarProdutor } from '../../support/api';
import { conferirCabecalhosDeSeguranca, violacoesDaCsp } from '../../support/cabecalhos';

/** Cabeçalhos de segurança do front-site (#202). */
test.describe('Cabeçalhos de segurança (front-site)', () => {
  test('páginas mandam CSP, anti-clickjacking e sem X-Powered-By', async ({ request }) => {
    for (const caminho of ['/', '/api/catalog']) {
      conferirCabecalhosDeSeguranca(await request.get(caminho));
    }
  });

  test('página inicial e do produto abrem sem violar a CSP', async ({ page }) => {
    const violacoes = violacoesDaCsp(page);
    const oferta = await criarOferta(await criarProdutor());

    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Produtos Disponíveis' })).toBeVisible();
    await page.goto(`/produto/${oferta.produtoId}?oferta=${oferta.estoqueId}`);
    await expect(page.getByRole('img').first()).toBeVisible();

    expect(violacoes).toEqual([]);
  });
});
