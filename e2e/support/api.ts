/**
 * api.ts — Prepara dados de teste chamando o backend real (nada de mock).
 *
 * Cada teste cria o próprio produtor com e-mail e CPF únicos, então os testes
 * não dependem de ordem nem de banco limpo e podem rodar em paralelo.
 */

import { APIRequestContext, expect, request } from '@playwright/test';

export const API_URL = process.env.API_URL ?? 'http://localhost:8080';

/** PNG 1x1 usado como foto de produto (o backend exige foto). */
export const PNG_1X1 = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64'
);

export const SENHA_PADRAO = 'Senha123';

let contador = 0;

/** Sufixo único por execução, worker e chamada. */
export function unico(): string {
  contador += 1;
  return `${Date.now().toString(36)}${process.pid.toString(36)}${contador}${Math.floor(Math.random() * 1e4)}`;
}

/** CPF válido (com dígitos verificadores), exigido pelo DocumentoValidator do backend. */
export function gerarCpf(): string {
  const base = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10));
  if (base.every((d) => d === base[0])) base[0] = (base[0] + 1) % 10;
  const digito = (nums: number[]) => {
    const soma = nums.reduce((acc, n, i) => acc + n * (nums.length + 1 - i), 0);
    const resto = (soma * 10) % 11;
    return resto === 10 ? 0 : resto;
  };
  const d1 = digito(base);
  const d2 = digito([...base, d1]);
  return [...base, d1, d2].join('');
}

export interface Produtor {
  id: string;
  nome: string;
  email: string;
  senha: string;
  accessToken: string;
  refreshToken: string;
}

export interface Oferta {
  produtoId: string;
  estoqueId: string;
  nomePopular: string;
  quantidade: number;
}

async function novoContexto(): Promise<APIRequestContext> {
  return request.newContext({ baseURL: API_URL });
}

export async function login(email: string, senha: string): Promise<{ accessToken: string; refreshToken: string }> {
  const api = await novoContexto();
  const res = await api.post('/auth/login', { data: { email, senha } });
  expect(res.status(), `login de ${email}`).toBe(200);
  const corpo = await res.json();
  await api.dispose();
  return { accessToken: corpo.accessToken, refreshToken: corpo.refreshToken };
}

/** Cadastra um proprietário pelo mesmo endpoint da tela de cadastro e já faz login. */
export async function criarProdutor(opcoes: { exibirNoSitePublico?: boolean } = {}): Promise<Produtor> {
  const id = unico();
  const nome = `Produtor E2E ${id}`;
  const email = `e2e-${id}@sementelivre.test`;
  const api = await novoContexto();
  const res = await api.post('/auth/cadastrar', {
    data: {
      nome,
      email,
      senha: SENHA_PADRAO,
      tipoDocumento: 'CPF',
      documento: gerarCpf(),
      rg: `MG${id.slice(-8)}`,
      exibirNoSitePublico: opcoes.exibirNoSitePublico ?? true,
      endereco: { logradouro: 'Rua das Sementes', numero: '10', municipio: 'Rio Pomba', uf: 'MG', cep: '36180000' },
    },
  });
  expect(res.status(), `cadastro de ${email}: ${await res.text()}`).toBe(201);
  const perfil = await res.json();
  await api.dispose();
  const tokens = await login(email, SENHA_PADRAO);
  return { id: perfil.id, nome, email, senha: SENHA_PADRAO, ...tokens };
}

async function autenticado(produtor: Produtor): Promise<APIRequestContext> {
  return request.newContext({
    baseURL: API_URL,
    extraHTTPHeaders: { Authorization: `Bearer ${produtor.accessToken}` },
  });
}

/** Produto (com foto enviada de verdade) + estoque do produtor, como o app faz em /sementes/nova. */
export async function criarOferta(
  produtor: Produtor,
  opcoes: {
    nomePopular?: string;
    quantidade?: number;
    preco?: number;
    disponibilidade?: 'PARA_VENDA' | 'PARA_TROCA' | 'PARA_DOACAO';
    aceitaNegociacao?: boolean;
  } = {}
): Promise<Oferta> {
  const api = await autenticado(produtor);
  const nomePopular = opcoes.nomePopular ?? `Feijão E2E ${unico()}`;
  const quantidade = opcoes.quantidade ?? 50;

  const upload = await api.post('/produtos/upload-foto', {
    multipart: { foto: { name: 'foto.png', mimeType: 'image/png', buffer: PNG_1X1 } },
  });
  expect(upload.ok(), `upload da foto: ${await upload.text()}`).toBeTruthy();
  const urlFoto = (await upload.text()).trim();

  const produto = await api.post('/produtos', {
    data: { nomePopular, nomeCientifico: 'Phaseolus vulgaris', urlFoto, tipo: 'LEGUMINOSA', especie: 'FEIJAO', formato: 'SEMENTE' },
  });
  expect(produto.ok(), `produto: ${await produto.text()}`).toBeTruthy();
  const { id: produtoId } = await produto.json();

  const estoque = await api.post('/estoques', {
    data: {
      proprietarioId: produtor.id,
      produtoId,
      descricao: 'Oferta criada pelo teste e2e',
      preco: opcoes.preco ?? 12.5,
      quantidade,
      tipoPesagem: 'KG',
      disponibilidade: opcoes.disponibilidade ?? 'PARA_VENDA',
      tipoMovimentacao: 'ENTRADA',
      aceitaNegociacao: opcoes.aceitaNegociacao ?? true,
    },
  });
  expect(estoque.ok(), `estoque: ${await estoque.text()}`).toBeTruthy();
  const { id: estoqueId } = await estoque.json();
  await api.dispose();
  return { produtoId, estoqueId, nomePopular, quantidade };
}

export async function buscarEstoque(produtor: Produtor, estoqueId: string): Promise<{ quantidade: number }> {
  const api = await autenticado(produtor);
  const res = await api.get(`/estoques/${estoqueId}`);
  expect(res.ok()).toBeTruthy();
  const corpo = await res.json();
  await api.dispose();
  return corpo;
}

export async function listarPedidos(produtor: Produtor): Promise<Array<{ id: string; status: string; tipoPedido: string }>> {
  const api = await autenticado(produtor);
  const res = await api.get(`/pedidos?proprietarioId=${produtor.id}`);
  expect(res.ok()).toBeTruthy();
  const corpo = await res.json();
  await api.dispose();
  return Array.isArray(corpo) ? corpo : corpo.content ?? [];
}

export interface PropriedadeSalva {
  id: string;
  nome: string;
  comunidadeNome: string;
  logradouroMunicipio: string;
}

export async function listarPropriedades(produtor: Produtor): Promise<PropriedadeSalva[]> {
  const api = await autenticado(produtor);
  const res = await api.get(`/api/propriedades?proprietarioId=${produtor.id}`);
  expect(res.ok()).toBeTruthy();
  const corpo = await res.json();
  await api.dispose();
  return corpo;
}
