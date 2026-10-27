import { readFile } from 'node:fs/promises';
import { request, type Page } from '@playwright/test';
import { test, expect, toast } from '../../support/fixtures';
import { API_URL, PNG_1X1, unico, type Produtor } from '../../support/api';

// Validação dos relatórios com dados reais (#103): cada filtro, a contagem da
// prévia contra o histórico e o conteúdo do CSV.

type Especie = 'FEIJAO' | 'MILHO';
type Disponibilidade = 'PARA_VENDA' | 'PARA_TROCA' | 'PARA_DOACAO';

interface Semente {
  produtoId: string;
  nomePopular: string;
}

async function api(produtor: Produtor) {
  return request.newContext({
    baseURL: API_URL,
    extraHTTPHeaders: { Authorization: `Bearer ${produtor.accessToken}` },
  });
}

async function criarSemente(produtor: Produtor, nome: string, especie: Especie, disponibilidade: Disponibilidade): Promise<Semente> {
  const contexto = await api(produtor);

  const upload = await contexto.post('/produtos/upload-foto', {
    multipart: { foto: { name: 'foto.png', mimeType: 'image/png', buffer: PNG_1X1 } },
  });
  const urlFoto = (await upload.text()).trim();

  const tipo = especie === 'FEIJAO' ? 'LEGUMINOSA' : 'CEREAL';
  const produto = await contexto.post('/produtos', {
    data: { nomePopular: nome, nomeCientifico: 'Teste', urlFoto, tipo, especie, formato: 'SEMENTE' },
  });
  expect(produto.ok(), await produto.text()).toBeTruthy();
  const { id: produtoId } = await produto.json();

  const estoque = await contexto.post('/estoques', {
    data: {
      proprietarioId: produtor.id,
      produtoId,
      preco: 10,
      quantidade: 100,
      tipoPesagem: 'KG',
      disponibilidade,
      tipoMovimentacao: 'ENTRADA',
      aceitaNegociacao: true,
    },
  });
  expect(estoque.ok(), await estoque.text()).toBeTruthy();

  await contexto.dispose();
  return { produtoId, nomePopular: nome };
}

async function criarPedido(produtor: Produtor, semente: Semente, tipoPedido: string, comprador: string, acao?: 'confirmar' | 'cancelar') {
  const contexto = await api(produtor);

  const resposta = await contexto.post('/pedidos', {
    data: {
      tipoPedido,
      proprietarioRecebedorId: produtor.id,
      itens: [{ produtoId: semente.produtoId, quantidade: 1 }],
      comprador: { nome: comprador, telefone: '(32) 99999-0000' },
    },
  });
  expect(resposta.ok(), await resposta.text()).toBeTruthy();
  const { id } = await resposta.json();

  if (acao) {
    const mudanca = await contexto.patch(`/pedidos/${id}/${acao}`);
    expect(mudanca.ok(), await mudanca.text()).toBeTruthy();
  }

  await contexto.dispose();
}

function hoje(dias = 0): string {
  const data = new Date();
  data.setDate(data.getDate() + dias);
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

async function gerar(page: Page) {
  await page.getByRole('button', { name: 'Gerar Relatório' }).click();
  await expect(toast(page, 'Relatório gerado e registrado!').last()).toBeVisible();
}

function chip(page: Page, grupo: string, nome: string) {
  return page.getByRole('group', { name: grupo }).getByRole('button', { name: nome, exact: true });
}

async function conferirHistorico(page: Page, quantidade: number) {
  const primeiraLinha = page.getByRole('table', { name: 'Histórico de relatórios emitidos' }).getByRole('row').nth(1);
  await expect(primeiraLinha.getByRole('cell').nth(2)).toHaveText(String(quantidade));
}

test.describe('Relatórios com dados reais (#103)', () => {
  test('estoque: filtros de disponibilidade e espécie batem com a prévia e o histórico', async ({ appLogado: page, produtor }) => {
    const id = unico();
    const feijaoVenda = await criarSemente(produtor, `Feijão Venda ${id}`, 'FEIJAO', 'PARA_VENDA');
    const milhoTroca = await criarSemente(produtor, `Milho Troca ${id}`, 'MILHO', 'PARA_TROCA');
    const feijaoDoacao = await criarSemente(produtor, `Feijão Doação ${id}`, 'FEIJAO', 'PARA_DOACAO');
    const tabela = page.getByRole('table', { name: 'Relatório de estoque' });

    await page.goto('/relatorios');
    await gerar(page);
    await expect(page.getByText('Prévia — 3 registros')).toBeVisible();
    await conferirHistorico(page, 3);

    await chip(page, 'Filtrar por disponibilidade', 'Para Venda').click();
    await expect(page.getByText('Nenhum relatório gerado')).toBeVisible();
    await gerar(page);
    await expect(page.getByText('Prévia — 1 registros')).toBeVisible();
    await expect(tabela.getByText(feijaoVenda.nomePopular)).toBeVisible();
    await conferirHistorico(page, 1);

    await chip(page, 'Filtrar por disponibilidade', 'Todas').click();
    await chip(page, 'Filtrar por espécie', 'Feijão').click();
    await gerar(page);
    await expect(page.getByText('Prévia — 2 registros')).toBeVisible();
    await expect(tabela.getByText(feijaoDoacao.nomePopular)).toBeVisible();
    await expect(tabela.getByText(milhoTroca.nomePopular)).toHaveCount(0);
    await conferirHistorico(page, 2);

    await chip(page, 'Filtrar por disponibilidade', 'Para Troca').click();
    await gerar(page);
    await expect(page.getByText('Sem dados')).toBeVisible();
    await expect(page.getByRole('button', { name: '⬇ PDF' })).toBeDisabled();
    await expect(page.getByRole('button', { name: '⬇ CSV' })).toBeDisabled();
    await conferirHistorico(page, 0);
  });

  test('mudar o filtro trava a exportação até gerar de novo', async ({ appLogado: page, produtor }) => {
    const id = unico();
    await criarSemente(produtor, `Feijão Venda ${id}`, 'FEIJAO', 'PARA_VENDA');
    await criarSemente(produtor, `Milho Troca ${id}`, 'MILHO', 'PARA_TROCA');
    const pdf = page.getByRole('button', { name: '⬇ PDF' });
    const csv = page.getByRole('button', { name: '⬇ CSV' });

    await page.goto('/relatorios');
    await expect(csv).toBeDisabled();
    await gerar(page);
    await expect(csv).toBeEnabled();

    // Sem a trava, o CSV saía com 1 registro enquanto o histórico só tinha o de 2
    await chip(page, 'Filtrar por disponibilidade', 'Para Troca').click();
    await expect(pdf).toBeDisabled();
    await expect(csv).toBeDisabled();

    await gerar(page);
    await conferirHistorico(page, 1);
    await expect(pdf).toBeEnabled();
    await expect(csv).toBeEnabled();
  });

  test('pedidos: filtros de tipo, status, semente e período', async ({ appLogado: page, produtor }) => {
    const id = unico();
    const feijao = await criarSemente(produtor, `Feijão Pedido ${id}`, 'FEIJAO', 'PARA_VENDA');
    const milho = await criarSemente(produtor, `Milho Pedido ${id}`, 'MILHO', 'PARA_TROCA');
    await criarPedido(produtor, feijao, 'VENDA', `Comprador Venda ${id}`, 'confirmar');
    await criarPedido(produtor, milho, 'TROCA', `Comprador Troca ${id}`);
    await criarPedido(produtor, feijao, 'DOACAO', `Comprador Doacao ${id}`, 'cancelar');
    const tabela = page.getByRole('table', { name: 'Relatório de pedidos' });

    await page.goto('/relatorios');
    await page.getByRole('radio', { name: '📋 Pedidos' }).click();
    await gerar(page);
    await expect(page.getByText('Prévia — 3 registros')).toBeVisible();

    await chip(page, 'Filtrar por tipo de pedido', 'Troca').click();
    await gerar(page);
    await expect(page.getByText('Prévia — 1 registros')).toBeVisible();
    await expect(tabela.getByText(`Comprador Troca ${id}`)).toBeVisible();
    await conferirHistorico(page, 1);

    await chip(page, 'Filtrar por tipo de pedido', 'Todos').click();
    await chip(page, 'Filtrar por status', 'Confirmado').click();
    await gerar(page);
    await expect(page.getByText('Prévia — 1 registros')).toBeVisible();
    await expect(tabela.getByText(`Comprador Venda ${id}`)).toBeVisible();

    await chip(page, 'Filtrar por status', 'Todos').click();
    await page.getByLabel('Produto').selectOption({ label: feijao.nomePopular });
    await gerar(page);
    await expect(page.getByText('Prévia — 2 registros')).toBeVisible();
    await expect(tabela.getByText(`Comprador Troca ${id}`)).toHaveCount(0);

    await page.getByLabel('Produto').selectOption({ label: 'Todos' });
    await page.getByLabel('Data início').fill(hoje());
    await page.getByLabel('Data fim').fill(hoje());
    await gerar(page);
    await expect(page.getByText('Prévia — 3 registros')).toBeVisible();

    await page.getByLabel('Data início').fill(hoje(1));
    await page.getByLabel('Data fim').fill(hoje(1));
    await gerar(page);
    await expect(page.getByText('Sem dados')).toBeVisible();
    await conferirHistorico(page, 0);
  });

  test('CSV respeita o filtro e não quebra com aspas e acentos', async ({ appLogado: page, produtor }) => {
    const id = unico();
    await criarSemente(produtor, `Feijão "Carioca" ${id}`, 'FEIJAO', 'PARA_VENDA');
    await criarSemente(produtor, `Milho ${id}`, 'MILHO', 'PARA_VENDA');

    await page.goto('/relatorios');
    await chip(page, 'Filtrar por espécie', 'Feijão').click();
    await gerar(page);

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: '⬇ CSV' }).click(),
    ]);
    const csv = await readFile((await download.path())!, 'utf-8');

    expect(csv.charCodeAt(0)).toBe(0xfeff);
    expect(csv).toContain(`"Feijão ""Carioca"" ${id}",100,KG`);
    expect(csv).not.toContain(`Milho ${id}`);
    expect(csv.trim().split('\n')).toHaveLength(2);
  });
});

test('notificação lida pode voltar a ser não lida', async ({ appLogado: page, produtor }) => {
  const semente = await criarSemente(produtor, `Feijão Aviso ${unico()}`, 'FEIJAO', 'PARA_VENDA');
  await criarPedido(produtor, semente, 'VENDA', 'Comprador Aviso');

  await page.goto('/notificacoes');
  await page.getByRole('button', { name: 'Marcar todas como lidas' }).click();
  await expect(page.getByRole('button', { name: / - nao lida$/ })).toHaveCount(0);

  await page.getByRole('button', { name: 'Marcar como não lida' }).first().click();
  await expect(page.getByRole('button', { name: / - nao lida$/ })).toHaveCount(1);

  await page.reload();
  await expect(page.getByRole('button', { name: / - nao lida$/ })).toHaveCount(1);

  await page.goto('/dashboard');
  await expect(page.getByRole('link', { name: 'Notificações, 1 não lidas' })).toBeVisible();
});
