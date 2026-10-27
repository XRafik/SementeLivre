import { test, expect } from '../../support/fixtures';
import { criarOferta } from '../../support/api';
import { registrarPedido } from '../../support/fluxos';

/** CDU de notificação: o backend notifica o produtor a cada pedido (CDU-26). */
test.describe('Notificações (front-app)', () => {
  test('conta sem pedidos não tem notificações', async ({ appLogado: page }) => {
    await page.goto('/notificacoes');
    await expect(page.getByText('Nenhuma notificacao')).toBeVisible();
  });

  test('pedido registrado gera notificação não lida; tocar só marca como lida', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor);
    await registrarPedido(page, oferta, 1, 'Recebedor Notificado');

    await page.goto('/dashboard');
    const sino = page.getByRole('link', { name: /^Notificações, \d+ não lidas$/ });
    await expect(sino).toBeVisible();
    await sino.click();
    await expect(page).toHaveURL(/\/notificacoes$/);

    const naoLida = page.getByRole('button', { name: / - nao lida$/ }).first();
    await expect(naoLida).toBeVisible();
    await naoLida.click();

    // Continua na lista: a notificação não é um atalho para o pedido (#180)
    await expect(page.getByRole('button', { name: / - nao lida$/ })).toHaveCount(0);
    await expect(page).toHaveURL(/\/notificacoes$/);

    // Tocar de novo numa já lida também não sai da tela
    await page.getByRole('button', { name: /^Pedido|^Novo pedido/ }).first().click();
    await expect(page).toHaveURL(/\/notificacoes$/);
    await page.reload();
    await expect(page.getByRole('button', { name: / - nao lida$/ })).toHaveCount(0);
  });

  test('marcar todas como lidas zera o contador', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 20 });
    await registrarPedido(page, oferta, 1, 'Primeiro');
    await registrarPedido(page, oferta, 1, 'Segundo');

    await page.goto('/notificacoes');
    await expect(page.getByRole('button', { name: / - nao lida$/ }).first()).toBeVisible();
    await page.getByRole('button', { name: 'Marcar todas como lidas' }).click();

    await expect(page.getByRole('button', { name: / - nao lida$/ })).toHaveCount(0);
    await page.reload();
    await expect(page.getByRole('button', { name: / - nao lida$/ })).toHaveCount(0);
  });
});
