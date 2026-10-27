import { request } from '@playwright/test';
import { test, expect } from '../../support/fixtures';
import { API_URL, criarOferta, Produtor } from '../../support/api';
import { registrarPedido } from '../../support/fluxos';

/** Histórico de estoque (TELA 09): tem que bater com o saldo a cada mudança (#185). */
test.describe('Movimentações de estoque (front-app)', () => {
  test('pedido confirmado aparece no histórico como saída', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 50 });
    await registrarPedido(page, oferta, 10, 'Comprador Historico');
    // Na fila o estoque não muda (#183); a saída nasce na confirmação
    await page.getByRole('button', { name: 'Confirmar Pedido' }).click();
    await expect(page.getByText('Confirmado', { exact: true })).toBeVisible();

    await page.goto(`/sementes/${oferta.estoqueId}/estoque`);
    const linhas = page.getByRole('listitem');
    await expect(page.getByText('saldo 40', { exact: false }).first()).toBeVisible();
    await expect(linhas.first()).toContainText('Saída — Venda');
    await expect(linhas.first()).toContainText('Pedido');
    await expect(linhas.first()).toContainText('saldo 50 → 40');
    await expect(linhas.first()).toContainText('Pedido de Comprador Historico');
  });

  test('cancelar o pedido confirmado devolve no histórico', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 50 });
    const idPedido = await registrarPedido(page, oferta, 10, 'Comprador Devolucao');
    await page.getByRole('button', { name: 'Confirmar Pedido' }).click();
    await expect(page.getByText('Confirmado', { exact: true })).toBeVisible();

    // O app só cancela pendentes; o confirmado é cancelado pela API
    await cancelarPelaApi(produtor, idPedido);

    await page.goto(`/sementes/${oferta.estoqueId}/estoque`);
    const primeira = page.getByRole('listitem').first();
    await expect(primeira).toContainText('Entrada');
    await expect(primeira).toContainText('Devolução');
    await expect(primeira).toContainText('saldo 40 → 50');
  });

  test('ajuste manual depois de uma venda fica como entrada', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 50 });

    // Saída rápida pela tela de histórico
    await page.goto(`/sementes/${oferta.estoqueId}/estoque`);
    await page.getByRole('button', { name: 'Nova movimentação' }).click();
    await page.getByLabel('Tipo').selectOption({ label: 'Saída — Venda' });
    await page.getByLabel('Quantidade').fill('3');
    await page.getByRole('button', { name: 'Registrar' }).click();
    await expect(page.getByRole('listitem').first()).toContainText('saldo 50 → 47');

    // "Ajustar Estoque > Adicionar 5" na tela do produto
    await page.goto(`/sementes/${oferta.estoqueId}`);
    await page.getByRole('button', { name: 'Ajustar Estoque' }).click();
    const dialogo = page.getByRole('dialog', { name: 'Ajustar Estoque' });
    await dialogo.getByLabel('Quantidade').fill('5');
    await dialogo.getByRole('button', { name: 'Confirmar' }).click();
    await expect(page.getByText('Estoque ajustado!')).toBeVisible();

    await page.goto(`/sementes/${oferta.estoqueId}/estoque`);
    const primeira = page.getByRole('listitem').first();
    await expect(primeira).toContainText('saldo 47 → 52');
    await expect(primeira).toContainText('Entrada');
    await expect(primeira).not.toContainText('Saída');
    await expect(primeira).toContainText('Ajuste manual de quantidade');
  });
});

async function cancelarPelaApi(produtor: Produtor, idPedido: string) {
  const api = await request.newContext({
    baseURL: API_URL,
    extraHTTPHeaders: { Authorization: `Bearer ${produtor.accessToken}` },
  });
  const res = await api.patch(`/pedidos/${idPedido}/cancelar`);
  expect(res.ok(), await res.text()).toBeTruthy();
  await api.dispose();
}
