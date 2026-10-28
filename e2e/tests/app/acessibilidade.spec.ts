import { test, expect } from '../../support/fixtures';
import { criarOferta } from '../../support/api';
import { registrarPedido } from '../../support/fluxos';
import { semViolacoesGraves } from '../../support/acessibilidade';

/** Padrão ARIA / WCAG nas telas do front-app (#182). */
test.describe('Acessibilidade (front-app)', () => {
  for (const tela of ['/entrar', '/cadastrar', '/recuperar-senha']) {
    test(`tela pública ${tela}`, async ({ page }) => {
      await page.goto(tela);
      await semViolacoesGraves(page, tela);
    });
  }

  test('telas logadas', async ({ appLogado: page, produtor }) => {
    test.setTimeout(120_000);
    const oferta = await criarOferta(produtor);
    const pedido = await registrarPedido(page, oferta, 1, 'Comprador Acessivel');

    for (const tela of [
      '/dashboard',
      '/produtos',
      '/produtos/nova',
      `/produtos/${oferta.estoqueId}`,
      `/produtos/${oferta.estoqueId}/estoque`,
      '/pedidos',
      '/pedidos/novo',
      `/pedidos/${pedido}`,
      '/notificacoes',
      '/perfil',
      '/propriedades',
      '/propriedades/nova',
      '/relatorios',
      '/catalogo',
    ]) {
      await page.goto(tela);
      await semViolacoesGraves(page, tela);
    }
  });

  test('diálogo: digitar não perde o foco, Tab fica preso e Esc devolve o foco', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor);
    await page.goto('/pedidos/novo');
    const abrir = page.getByRole('button', { name: '+ Adicionar' });
    await abrir.click();
    const dialogo = page.getByRole('dialog', { name: 'Selecionar Produto' });
    await dialogo.getByRole('button', { name: new RegExp(oferta.nomePopular) }).click();

    // Antes cada renderização jogava o foco no botão Fechar e o campo ficava vazio
    const quantidade = dialogo.getByLabel(/Quantidade \(máx/);
    await quantidade.click();
    await page.keyboard.type('12', { delay: 120 });
    await expect(quantidade).toHaveValue('12');
    await expect(quantidade).toBeFocused();

    // Tab a partir do último elemento volta para o primeiro, sem sair do diálogo
    for (let i = 0; i < 12; i++) {
      await page.keyboard.press('Tab');
      expect(await dialogo.evaluate((el) => el.contains(document.activeElement))).toBe(true);
    }

    await page.keyboard.press('Escape');
    await expect(dialogo).toBeHidden();
    await expect(abrir).toBeFocused();
  });

  test('navegação marca a página atual e o botão de senha diz o que faz', async ({ page, appLogado }) => {
    void appLogado;
    const nav = page.getByRole('navigation', { name: 'Navegação principal' });
    await page.goto('/pedidos');
    await expect(nav.getByRole('link', { name: 'Pedidos' })).toHaveAttribute('aria-current', 'page');
    await expect(nav.getByRole('link', { name: 'Produtos' })).not.toHaveAttribute('aria-current', 'page');
    await expect(nav.getByRole('tab')).toHaveCount(0);

    // Sai: sem o cookie da sessão e sem o access token da aba
    await page.context().clearCookies();
    await page.evaluate(() => sessionStorage.clear());
    await page.goto('/entrar');
    await page.getByRole('button', { name: 'Mostrar senha' }).click();
    await expect(page.getByLabel(/^Senha/)).toHaveAttribute('type', 'text');
    await expect(page.getByRole('button', { name: 'Ocultar senha' })).toBeVisible();
  });

  test('diálogo aberto', async ({ appLogado: page, produtor }) => {
    await criarOferta(produtor);
    await page.goto('/pedidos/novo');
    await page.getByRole('button', { name: '+ Adicionar' }).click();
    const dialogo = page.getByRole('dialog', { name: 'Selecionar Produto' });
    await expect(dialogo).toBeVisible();
    await expect(dialogo).toHaveAttribute('aria-modal', 'true');

    await semViolacoesGraves(page, 'diálogo Selecionar Produto');
  });
});
