import { test, expect, toast } from '../../support/fixtures';
import { gerarCpf, login, SENHA_PADRAO, unico } from '../../support/api';

/** CDU de autenticação: cadastro, login e logout no front-app. */
test.describe('Auth (front-app)', () => {
  test('cadastro de proprietário cria a conta e leva ao login', async ({ page }) => {
    const id = unico();
    const email = `e2e-cadastro-${id}@sementelivre.test`;

    await page.goto('/cadastrar');
    await page.getByLabel('Nome completo').fill(`Proprietária ${id}`);
    await page.getByLabel('CPF').fill(gerarCpf());
    await page.getByLabel('RG').fill(`MG${id.slice(-8)}`);
    await page.getByLabel('E-mail').fill(email);
    await page.getByLabel(/^Senha/).fill(SENHA_PADRAO);
    await page.getByLabel('Confirmar Senha').fill(SENHA_PADRAO);
    await page.getByLabel('Logradouro').fill('Estrada do Quilombo');
    await page.getByLabel('Município').fill('Rio Pomba');
    await page.getByLabel('UF').fill('mg');
    await page.getByRole('button', { name: 'Criar Conta' }).click();

    await expect(toast(page, 'Conta criada com sucesso!')).toBeVisible();
    await expect(page).toHaveURL(/\/entrar$/);
    // A conta existe de verdade no backend
    await login(email, SENHA_PADRAO);
  });

  test('cadastro mostra erros de validação sem chamar o backend', async ({ page }) => {
    await page.goto('/cadastrar');
    await page.getByLabel('E-mail').fill('email-invalido');
    await page.getByLabel(/^Senha/).fill('fraca');
    await page.getByRole('button', { name: 'Criar Conta' }).click();

    await expect(page.locator('[aria-invalid="true"]').first()).toBeVisible();
    await expect(page).toHaveURL(/\/cadastrar$/);
  });

  test('cadastro com e-mail já usado mostra o erro da API no campo', async ({ page, produtor }) => {
    await page.goto('/cadastrar');
    await page.getByLabel('Nome completo').fill('Pessoa Duplicada');
    await page.getByLabel('CPF').fill(gerarCpf());
    await page.getByLabel('RG').fill('MG1234567');
    await page.getByLabel('E-mail').fill(produtor.email);
    await page.getByLabel(/^Senha/).fill(SENHA_PADRAO);
    await page.getByLabel('Confirmar Senha').fill(SENHA_PADRAO);
    await page.getByLabel('Logradouro').fill('Rua A');
    await page.getByLabel('Município').fill('Rio Pomba');
    await page.getByLabel('UF').fill('MG');
    await page.getByRole('button', { name: 'Criar Conta' }).click();

    await expect(page.getByRole('alert').filter({ hasText: /e-mail|email/i }).first()).toBeVisible();
    await expect(page).toHaveURL(/\/cadastrar$/);
  });

  test('login com credenciais válidas abre o dashboard', async ({ page, produtor }) => {
    await page.goto('/entrar');
    await page.getByLabel('E-mail').fill(produtor.email);
    await page.getByLabel(/^Senha/).fill(produtor.senha);
    await page.getByRole('button', { name: 'Entrar' }).click();

    await expect(page).toHaveURL(/\/dashboard$/);
    await expect(page.getByRole('navigation', { name: 'Navegação principal' })).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('sl_access_token'))).toBeTruthy();
  });

  test('login com senha errada avisa e continua na tela de login', async ({ page, produtor }) => {
    await page.goto('/entrar');
    await page.getByLabel('E-mail').fill(produtor.email);
    await page.getByLabel(/^Senha/).fill('SenhaErrada9');
    await page.getByRole('button', { name: 'Entrar' }).click();

    await expect(toast(page, 'E-mail ou senha incorretos.')).toBeVisible();
    await expect(page).toHaveURL(/\/entrar$/);
  });

  test('login com campos vazios pede preenchimento', async ({ page }) => {
    await page.goto('/entrar');
    await page.getByRole('button', { name: 'Entrar' }).click();
    await expect(page.getByRole('alert').filter({ hasText: 'Preencha todos os campos.' })).toBeVisible();
  });

  test('logout limpa a sessão e protege as rotas internas', async ({ appLogado: page }) => {
    await page.getByRole('navigation', { name: 'Navegação principal' }).getByRole('link', { name: 'Perfil' }).click();
    await page.getByRole('button', { name: 'Sair do Aplicativo' }).click();
    await page.getByRole('dialog').getByRole('button', { name: 'Sair', exact: true }).click();

    await expect(page).toHaveURL(/\/entrar$/);
    expect(await page.evaluate(() => localStorage.getItem('sl_access_token'))).toBeNull();

    await page.goto('/pedidos');
    await expect(page).toHaveURL(/\/entrar$/);
  });
});
