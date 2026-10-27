import { test, expect } from '@playwright/test';
import { criarOferta, criarProdutor } from '../../support/api';

/** Estados de erro do front-site. */
test.describe('Estados de erro (front-site)', () => {
  test('produto inexistente mostra "não encontrado" e volta ao catálogo', async ({ page }) => {
    await page.goto('/produto/00000000-0000-0000-0000-000000000000');
    await expect(page.getByText('Produto não encontrado.')).toBeVisible();
    await page.getByRole('button', { name: 'Voltar ao catálogo' }).click();
    await expect(page).toHaveURL(/\/$/);
  });

  test('catálogo indisponível avisa o visitante', async ({ page }) => {
    // Simula o proxy do Next sem backend: a rota /api/catalog responde 502 como faria
    await page.route('**/api/catalog', (route) =>
      route.fulfill({ status: 502, json: { message: 'Não foi possível falar com o servidor.' } })
    );
    await page.goto('/');
    await expect(page.getByText('Erro ao carregar produtos.')).toBeVisible();
  });

  test('pedido sem nome e e-mail não é enviado', async ({ page }) => {
    const produtor = await criarProdutor();
    const oferta = await criarOferta(produtor);

    await page.goto(`/produto/${oferta.produtoId}?oferta=${oferta.estoqueId}`);
    await page.getByRole('button', { name: 'Enviar pedido' }).click();
    // O navegador bloqueia pelo required ou o site avisa pelo toast
    await expect(page).toHaveURL(new RegExp(`/produto/${oferta.produtoId}`));
  });

  test('quantidade acima do estoque é recusada pelo backend com mensagem', async ({ page }) => {
    const produtor = await criarProdutor();
    const oferta = await criarOferta(produtor, { quantidade: 2 });

    await page.goto(`/produto/${oferta.produtoId}?oferta=${oferta.estoqueId}`);
    // Remove o max do input para o pedido chegar ao backend, que é quem garante o estoque
    await page.locator('#quantidade').evaluate((el) => el.removeAttribute('max'));
    await page.locator('#quantidade').fill('50');
    await page.getByLabel(/Seu nome/).fill('Cliente Exagerado');
    await page.getByLabel(/E-mail/).fill('exagerado@sementelivre.test');
    await page.getByRole('button', { name: 'Enviar pedido' }).click();

    await expect(page.locator('.Toastify__toast--error')).toBeVisible();
    await expect(page).toHaveURL(new RegExp(`/produto/${oferta.produtoId}`));
  });
});
