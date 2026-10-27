import { test } from '@playwright/test';
import { criarOferta, criarProdutor } from '../../support/api';
import { semViolacoesGraves } from '../../support/acessibilidade';

/** Padrão ARIA / WCAG nas telas do front-site (#182). */
test.describe('Acessibilidade (front-site)', () => {
  test('página inicial', async ({ page }) => {
    await page.goto('/');
    await semViolacoesGraves(page, '/');
  });

  test('página do produto com o formulário de pedido', async ({ page }) => {
    const oferta = await criarOferta(await criarProdutor());
    await page.goto(`/produto/${oferta.produtoId}?oferta=${oferta.estoqueId}`);
    await semViolacoesGraves(page, '/produto');
  });
});
