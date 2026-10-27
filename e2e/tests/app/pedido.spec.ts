import { test, expect, toast } from '../../support/fixtures';
import { buscarEstoque, criarOferta, listarPedidos } from '../../support/api';
import { registrarPedido } from '../../support/fluxos';

/** CDU de pedido: registrar, confirmar e cancelar no front-app. */
test.describe('Pedidos (front-app)', () => {
  test('registrar pedido entra na fila sem baixar o estoque (#183)', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 40 });
    const id = await registrarPedido(page, oferta, 5, 'Maria Recebedora');

    await expect(page.getByText('Maria Recebedora')).toBeVisible();
    await expect(page.getByText('Pendente')).toBeVisible();
    await expect(page.getByText('Total: R$ 62.50')).toBeVisible();
    await expect(page.getByRole('note')).toContainText('o estoque só é baixado quando você confirmar');
    expect((await buscarEstoque(produtor, oferta.estoqueId)).quantidade).toBe(40);

    await page.getByRole('tab', { name: 'Pedidos' }).click();
    await expect(page.getByText('Maria Recebedora')).toBeVisible();
    expect((await listarPedidos(produtor)).map((p) => p.id)).toContain(id);
  });

  test('confirmar pedido muda o status para confirmado', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 10 });
    const id = await registrarPedido(page, oferta, 2, 'João Confirmado');

    await page.getByRole('button', { name: 'Confirmar Pedido' }).click();

    await expect(toast(page, 'Pedido confirmado!')).toBeVisible();
    await expect(page.getByText('Confirmado', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Confirmar Pedido' })).toHaveCount(0);
    expect((await listarPedidos(produtor)).find((p) => p.id === id)?.status).toBe('CONFIRMADO');
    expect((await buscarEstoque(produtor, oferta.estoqueId)).quantidade).toBe(8);
  });

  test('cancelar pedido da fila não mexe no estoque', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 10 });
    const id = await registrarPedido(page, oferta, 4, 'Ana Cancelada');
    expect((await buscarEstoque(produtor, oferta.estoqueId)).quantidade).toBe(10);

    await page.getByRole('button', { name: 'Cancelar Pedido' }).click();
    await page.getByRole('dialog', { name: 'Cancelar Pedido' }).getByRole('button', { name: 'Cancelar Pedido' }).click();

    await expect(toast(page, 'Pedido cancelado e retirado da fila.')).toBeVisible();
    await expect(page.getByText('Cancelado', { exact: true })).toBeVisible();
    expect((await listarPedidos(produtor)).find((p) => p.id === id)?.status).toBe('CANCELADO');
    expect((await buscarEstoque(produtor, oferta.estoqueId)).quantidade).toBe(10);
  });

  test('confirmar o pedido que esgota o estoque recusa quem não cabe mais (#183)', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 50 });
    const grande = await registrarPedido(page, oferta, 49, 'Pedido Grande');
    const pequeno = await registrarPedido(page, oferta, 10, 'Pedido Pequeno');
    expect((await buscarEstoque(produtor, oferta.estoqueId)).quantidade).toBe(50);

    await page.goto(`/pedidos/${grande}`);
    await page.getByRole('button', { name: 'Confirmar Pedido' }).click();
    await expect(toast(page, 'Pedido confirmado! O estoque foi atualizado.')).toBeVisible();

    // Sobrou 1 kg: o pedido de 10 sai da fila e a vitrine passa a mostrar 1
    expect((await buscarEstoque(produtor, oferta.estoqueId)).quantidade).toBe(1);
    expect((await listarPedidos(produtor)).find((p) => p.id === pequeno)?.status).toBe('CANCELADO');

    await page.goto(`/pedidos/${pequeno}`);
    await expect(page.getByText('Cancelado', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Confirmar Pedido' })).toHaveCount(0);

    await page.goto('/notificacoes');
    await expect(page.getByRole('button', { name: /^Pedido recusado/ })).toBeVisible();
  });

  test('confirmar mantém na fila quem ainda cabe (#183)', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 50 });
    const grande = await registrarPedido(page, oferta, 40, 'Pedido Quarenta');
    const pequeno = await registrarPedido(page, oferta, 10, 'Pedido Dez');

    await page.goto(`/pedidos/${grande}`);
    await page.getByRole('button', { name: 'Confirmar Pedido' }).click();
    await expect(toast(page, 'Pedido confirmado! O estoque foi atualizado.')).toBeVisible();

    expect((await buscarEstoque(produtor, oferta.estoqueId)).quantidade).toBe(10);
    expect((await listarPedidos(produtor)).find((p) => p.id === pequeno)?.status).toBe('PENDENTE');
  });

  test('pedido sem recebedor e sem itens não é enviado', async ({ appLogado: page }) => {
    await page.goto('/pedidos/novo');
    await page.getByRole('button', { name: 'Registrar Pedido' }).click();

    await expect(page.getByText('Nome é obrigatório')).toBeVisible();
    await expect(page.getByText('Adicione ao menos um item')).toBeVisible();
    await expect(page).toHaveURL(/\/pedidos\/novo$/);
  });

  test('quantidade acima do estoque é recusada', async ({ appLogado: page, produtor }) => {
    const oferta = await criarOferta(produtor, { quantidade: 3 });
    await page.goto('/pedidos/novo');
    await page.getByRole('button', { name: '+ Adicionar' }).click();
    const dialogo = page.getByRole('dialog', { name: 'Selecionar Semente' });
    await dialogo.getByRole('button', { name: new RegExp(oferta.nomePopular) }).click();
    await dialogo.getByLabel(/Quantidade \(máx/).fill('99');
    await dialogo.getByRole('button', { name: 'Adicionar', exact: true }).click();

    await expect(toast(page, 'Quantidade maior que o estoque disponível.')).toBeVisible();
  });

  test('pedido inexistente mostra erro e volta para a lista', async ({ appLogado: page }) => {
    await page.goto('/pedidos/00000000-0000-0000-0000-000000000000');
    // O botão "Voltar" do header usa router.back(); o do estado vazio leva à lista
    const voltar = page.getByRole('main').getByRole('button', { name: 'Voltar' });
    await expect(voltar).toBeVisible();
    await voltar.click();
    await expect(page).toHaveURL(/\/pedidos$/);
  });
});
