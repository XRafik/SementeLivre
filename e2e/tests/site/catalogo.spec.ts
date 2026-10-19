import { test, expect } from '@playwright/test';
import { buscarEstoque, criarOferta, criarProdutor, listarPedidos, unico } from '../../support/api';

/** CDU de catálogo público (front-site): listagem, busca, filtros e detalhe do produto. */
test.describe('Catálogo público (front-site)', () => {
  test('lista a oferta cadastrada no backend e busca por nome', async ({ page }) => {
    const produtor = await criarProdutor();
    const oferta = await criarOferta(produtor, { nomePopular: `Abóbora Catálogo ${unico()}`, quantidade: 15, preco: 7 });

    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Banco Comunitário de Sementes' })).toBeVisible();
    await page.getByPlaceholder('Buscar por nome popular...').fill(oferta.nomePopular);

    await expect(page.getByText('1 resultado', { exact: true })).toBeVisible();
    const card = page.locator('[data-slot="card"]').filter({ hasText: oferta.nomePopular });
    await expect(card).toBeVisible();
    await expect(card.getByText('15 kg em estoque')).toBeVisible();
    await expect(card.getByText('R$ 7.00/kg')).toBeVisible();
  });

  test('busca sem resultado mostra estado vazio', async ({ page }) => {
    await page.goto('/');
    const termo = `inexistente-${unico()}`;
    await page.getByPlaceholder('Buscar por nome popular...').fill(termo);
    await expect(page.getByText(`Nenhuma semente encontrada para "${termo}".`)).toBeVisible();
  });

  test('filtro de disponibilidade separa venda e doação', async ({ page }) => {
    const produtor = await criarProdutor();
    const sufixo = unico();
    const venda = await criarOferta(produtor, { nomePopular: `Venda ${sufixo}`, disponibilidade: 'PARA_VENDA' });
    const doacao = await criarOferta(produtor, { nomePopular: `Doação ${sufixo}`, disponibilidade: 'PARA_DOACAO', preco: 0 });

    await page.goto('/');
    await page.getByPlaceholder('Buscar por nome popular...').fill(sufixo);
    await expect(page.getByText('2 resultados')).toBeVisible();

    await page.getByRole('button', { name: 'Para Doação', exact: true }).click();
    await expect(page.getByText(doacao.nomePopular)).toBeVisible();
    await expect(page.getByText(venda.nomePopular)).toHaveCount(0);
  });

  test('detalhe do produto abre a partir do card', async ({ page }) => {
    const produtor = await criarProdutor();
    const oferta = await criarOferta(produtor, { nomePopular: `Feijão Detalhe ${unico()}` });

    await page.goto('/');
    await page.getByPlaceholder('Buscar por nome popular...').fill(oferta.nomePopular);
    const card = page.locator('[data-slot="card"]').filter({ hasText: oferta.nomePopular });
    await card.hover();
    await card.getByRole('button', { name: /Quero negociar|Fazer pedido/ }).click();

    await expect(page).toHaveURL(new RegExp(`/produto/${oferta.produtoId}\\?oferta=${oferta.estoqueId}`));
    await expect(page.getByRole('heading', { level: 1, name: oferta.nomePopular })).toBeVisible();
    await expect(page.getByText('Phaseolus vulgaris').first()).toBeVisible();
    await expect(page.getByText('Fazer pedido', { exact: true })).toBeVisible();
  });

  test('pedido público chega ao produtor com o estoque reservado', async ({ page }) => {
    const produtor = await criarProdutor();
    const oferta = await criarOferta(produtor, { nomePopular: `Milho Pedido ${unico()}`, quantidade: 20 });
    const cliente = `Cliente Site ${unico()}`;

    await page.goto(`/produto/${oferta.produtoId}?oferta=${oferta.estoqueId}`);
    await page.getByLabel(/^Quantidade/).fill('3');
    await page.getByLabel(/Seu nome/).fill(cliente);
    await page.getByLabel(/E-mail/).fill(`cliente-${unico()}@sementelivre.test`);
    await page.getByLabel('Mensagem (opcional)').fill('Retiro na feira de sábado.');
    await page.getByRole('button', { name: 'Enviar pedido' }).click();

    await expect(page.getByText('Pedido enviado! Uma cópia vai para o e-mail informado.')).toBeVisible();
    await expect(page).toHaveURL(/\/$/);

    const pedidos = await listarPedidos(produtor);
    expect(pedidos).toHaveLength(1);
    expect(pedidos[0]).toMatchObject({ status: 'PENDENTE', tipoPedido: 'VENDA' });
    expect((await buscarEstoque(produtor, oferta.estoqueId)).quantidade).toBe(17);
  });

  test('oferta sem negociação trava o tipo do pedido', async ({ page }) => {
    const produtor = await criarProdutor();
    const oferta = await criarOferta(produtor, { disponibilidade: 'PARA_TROCA', aceitaNegociacao: false });

    await page.goto(`/produto/${oferta.produtoId}?oferta=${oferta.estoqueId}`);
    await expect(page.getByText('O produtor não aceita negociar: só troca.')).toBeVisible();
    await expect(page.locator('#tipoPedido')).toHaveCount(0);
  });
});
