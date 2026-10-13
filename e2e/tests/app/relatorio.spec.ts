import { readFile } from 'node:fs/promises';
import { test, expect, toast } from '../../support/fixtures';
import { API_URL, criarOferta } from '../../support/api';
import { registrarPedido } from '../../support/fluxos';

/** CDU de relatório: gera a prévia, registra a emissão no backend e exporta PDF/CSV. */
test.describe('Relatórios (front-app)', () => {
  test('relatório de estoque gera prévia, histórico, PDF e CSV', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 25 });
    await page.goto('/relatorios');

    await page.getByRole('button', { name: 'Gerar Relatório' }).click();
    await expect(toast(page, 'Relatório gerado e registrado!')).toBeVisible();
    await expect(page.getByText('Prévia — 1 registros')).toBeVisible();
    await expect(page.getByRole('table', { name: 'Relatório de estoque' }).getByText(oferta.nomePopular)).toBeVisible();
    // A emissão fica no histórico do backend
    await expect(page.getByRole('table', { name: 'Histórico de relatórios emitidos' }).getByText('Estoque de sementes')).toBeVisible();

    const [pdf] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: '⬇ PDF' }).click()]);
    expect(pdf.suggestedFilename()).toMatch(/\.pdf$/);
    const conteudoPdf = await readFile((await pdf.path())!);
    expect(conteudoPdf.subarray(0, 5).toString()).toBe('%PDF-');
    await expect(toast(page, 'Relatório PDF exportado!')).toBeVisible();

    const [csv] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: '⬇ CSV' }).click()]);
    expect(csv.suggestedFilename()).toMatch(/^relatorio-estoque-\d+\.csv$/);
    const conteudoCsv = await readFile((await csv.path())!, 'utf-8');
    expect(conteudoCsv).toContain('Nome,Quantidade,Unidade,Disponibilidade,Preço');
    expect(conteudoCsv).toContain(`"${oferta.nomePopular}",25,KG`);
  });

  test('relatório de pedidos lista os pedidos do produtor e exporta CSV', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 10 });
    await registrarPedido(page, oferta, 1, 'Cliente do Relatório');

    await page.goto('/relatorios');
    await page.getByRole('radio', { name: '📋 Pedidos' }).click();
    await page.getByRole('button', { name: 'Gerar Relatório' }).click();

    await expect(toast(page, 'Relatório gerado e registrado!')).toBeVisible();
    const tabela = page.getByRole('table', { name: 'Relatório de pedidos' });
    await expect(tabela.getByText('Cliente do Relatório')).toBeVisible();

    const [csv] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: '⬇ CSV' }).click()]);
    const conteudo = await readFile((await csv.path())!, 'utf-8');
    expect(conteudo).toContain('ID,Data,Recebedor,Tipo,Status,Total');
    expect(conteudo).toContain('"Cliente do Relatório","Venda","Pendente"');

    const [pdf] = await Promise.all([page.waitForEvent('download'), page.getByRole('button', { name: '⬇ PDF' }).click()]);
    expect((await readFile((await pdf.path())!)).subarray(0, 5).toString()).toBe('%PDF-');
  });

  test('sem dados os botões de exportar ficam desabilitados', async ({ appLogado: page }) => {
    await page.goto('/relatorios');
    await page.getByRole('button', { name: 'Gerar Relatório' }).click();

    await expect(page.getByText('Sem dados')).toBeVisible();
    await expect(page.getByRole('button', { name: '⬇ PDF' })).toBeDisabled();
    await expect(page.getByRole('button', { name: '⬇ CSV' })).toBeDisabled();
  });

  test('relatório gerado antes da lista carregar continua no histórico', async ({ appLogado: page }) => {
    test.fixme(true, 'Corrida no useRelatorios corrigida no PR #162; remova o fixme quando ele entrar no developer');
    // Segura a resposta da lista inicial até depois da emissão (o dado continua vindo do backend)
    let liberar: () => void = () => {};
    const segurando = new Promise<void>((resolve) => (liberar = resolve));
    await page.route(`${API_URL}/relatorios`, async (route) => {
      if (route.request().method() !== 'GET') return route.continue();
      const resposta = await route.fetch();
      await segurando;
      await route.fulfill({ response: resposta });
    });

    await page.goto('/relatorios');
    await page.getByRole('button', { name: 'Gerar Relatório' }).click();
    await expect(toast(page, 'Relatório gerado e registrado!')).toBeVisible();
    liberar();

    await expect(page.getByRole('table', { name: 'Histórico de relatórios emitidos' }).getByText('Estoque de sementes')).toBeVisible();
  });
});
