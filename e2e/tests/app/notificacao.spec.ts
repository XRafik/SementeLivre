import { test, expect } from '../../support/fixtures';
import { criarOferta } from '../../support/api';
import { registrarPedido } from '../../support/fluxos';

/** CDU de notificação: o backend notifica o produtor a cada pedido (CDU-26). */
test.describe('Notificações (front-app)', () => {
  test('conta sem pedidos não tem notificações', async ({ appLogado: page }) => {
    await page.goto('/notificacoes');
    await expect(page.getByText('Nenhuma notificacao')).toBeVisible();
  });

  test('pedido registrado gera notificação não lida que leva ao pedido', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor);
    const idPedido = await registrarPedido(page, oferta, 1, 'Recebedor Notificado');

    await page.goto('/dashboard');
    const sino = page.getByRole('link', { name: /^Notificações, \d+ não lidas$/ });
    await expect(sino).toBeVisible();
    await sino.click();
    await expect(page).toHaveURL(/\/notificacoes$/);

    const naoLida = page.getByRole('button', { name: / - nao lida$/ }).first();
    await expect(naoLida).toBeVisible();
    await naoLida.click();

    await expect(page).toHaveURL(new RegExp(`/pedidos/${idPedido}$`));
    await page.goto('/notificacoes');
    await expect(page.getByRole('button', { name: / - nao lida$/ })).toHaveCount(0);
  });

  test('notificação de pedido cancelado aparece em salmão (#179)', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor);
    await registrarPedido(page, oferta, 1, 'Pedido Cancelado');
    await page.getByRole('button', { name: 'Cancelar Pedido' }).click();
    await page.getByRole('dialog', { name: 'Cancelar Pedido' }).getByRole('button', { name: 'Cancelar Pedido' }).click();
    await expect(page.getByText('Cancelado').first()).toBeVisible();

    await page.goto('/notificacoes');
    const cancelada = page.getByRole('button', { name: /^Pedido cancelado/ });
    const recebida = page.getByRole('button', { name: /^Novo pedido recebido/ });
    await expect(cancelada).toBeVisible();

    // Fundo salmão (#ffe8e3) só no cancelado; o pedido novo segue verde
    await expect(cancelada).toHaveCSS('background-color', 'rgb(255, 232, 227)');
    await expect(recebida).not.toHaveCSS('background-color', 'rgb(255, 232, 227)');

    // Continua salmão depois de lida
    await page.getByRole('button', { name: 'Marcar todas como lidas' }).click();
    await expect(page.getByRole('button', { name: / - nao lida$/ })).toHaveCount(0);
    await expect(cancelada).toHaveCSS('background-color', 'rgb(255, 232, 227)');
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
