import { test, expect, toast } from '../../support/fixtures';
import { PNG_1X1, unico } from '../../support/api';

/** Cadastro de produto com foto: é o que alimenta o catálogo público. */
test.describe('Produtos (front-app)', () => {
  test('cadastra produto com foto e estoque inicial', async ({ appLogado: page }) => {
    const nome = `Milho E2E ${unico()}`;
    await page.goto('/produtos/nova');
    await page.getByLabel('Arquivo de foto').setInputFiles({ name: 'milho.png', mimeType: 'image/png', buffer: PNG_1X1 });
    await expect(page.getByAltText('Preview do produto')).toBeVisible();
    await page.getByLabel('Nome popular').fill(nome);
    await page.getByLabel('Nome científico').fill('Zea mays');
    await page.getByLabel('Tipo').selectOption('CEREAL');
    await page.getByLabel('Espécie').selectOption('MILHO');
    await page.getByLabel('Quantidade').fill('30');
    await page.getByLabel('Preço (R$)').fill('8.5');
    await page.getByRole('button', { name: 'Cadastrar Produto' }).click();

    await expect(toast(page, 'Produto cadastrado com sucesso!')).toBeVisible();
    await expect(page).toHaveURL(/\/produtos$/);
    await expect(page.getByText(nome)).toBeVisible();
  });

  test('endereços antigos /sementes levam para /produtos (#186)', async ({ appLogado: page }) => {
    await page.goto('/sementes');
    await expect(page).toHaveURL(/\/produtos$/);

    await page.goto('/sementes/nova');
    await expect(page).toHaveURL(/\/produtos\/nova$/);
    await expect(page.getByRole('button', { name: 'Cadastrar Produto' })).toBeVisible();
  });

  test('exige foto, nome e quantidade', async ({ appLogado: page }) => {
    await page.goto('/produtos/nova');
    await page.getByRole('button', { name: 'Cadastrar Produto' }).click();

    await expect(page.getByText('Foto é obrigatória')).toBeVisible();
    await expect(page.getByText('Nome popular é obrigatório')).toBeVisible();
    await expect(page.getByText('Quantidade inválida')).toBeVisible();
  });
});
