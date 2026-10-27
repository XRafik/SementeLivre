import { Page } from '@playwright/test';
import { expect, toast } from './fixtures';
import { Oferta } from './api';

/** Registra um pedido pela tela /pedidos/novo e devolve o id (da URL de detalhe). */
export async function registrarPedido(page: Page, oferta: Oferta, quantidade: number, recebedor: string): Promise<string> {
  await page.goto('/pedidos/novo');
  await page.getByLabel('Nome completo').fill(recebedor);
  await page.getByLabel('Contato (telefone ou e-mail)').fill('(32) 99999-0000');
  await page.getByRole('button', { name: '+ Adicionar' }).click();

  const dialogo = page.getByRole('dialog', { name: 'Selecionar Produto' });
  await dialogo.getByRole('button', { name: new RegExp(oferta.nomePopular) }).click();
  await dialogo.getByLabel(/Quantidade \(máx/).fill(String(quantidade));
  await dialogo.getByRole('button', { name: 'Adicionar', exact: true }).click();

  await expect(page.getByRole('listitem').filter({ hasText: oferta.nomePopular })).toBeVisible();
  await page.getByRole('button', { name: 'Registrar Pedido' }).click();

  await expect(toast(page, 'Pedido registrado com sucesso!')).toBeVisible();
  await expect(page).toHaveURL(/\/pedidos\/[0-9a-f-]{36}$/);
  return page.url().split('/').pop()!;
}
