/**
 * authApi.ts — Funções que integram com os endpoints de autenticação e de conta do backend.
 *
 * Endpoints consumidos:
 *  POST /auth/login             → retorna { accessToken, refreshToken, tokenType, expiresInSeconds }
 *  POST /auth/cadastrar         → cria proprietário + conta de login; retorna PerfilUsuario (201 Created)
 *  POST /auth/refresh           → retorna { accessToken, refreshToken, tokenType, expiresInSeconds } (via api.ts)
 *  POST /auth/recuperar-senha   → retorna { mensagem: string }
 *  POST /auth/redefinir-senha   → retorna { mensagem: string }
 *  GET  /auth/me                → retorna PerfilUsuario (conta autenticada, com endereço e RG)
 *  POST /auth/alterar-senha     → retorna { mensagem: string }; 400 com fieldErrors "senhaAtual" se a atual não confere
 *  PUT  /api/proprietarios/{id} → atualiza dados pessoais e endereço do próprio proprietário
 *  DELETE /api/pessoas/{id}/conta → exclui a conta do titular (LGPD); 204
 */

import { apiGet, apiPost, apiPut, apiDelete, saveTokens, clearTokens, ApiError } from './api';
import { PerfilUsuario, PessoaUpdateRequest, Proprietario, ProprietarioCadastroRequest } from '@/types/user';

// ── DTOs (espelham os Records do backend) ─────────────────────────────────────

export interface TokenResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresInSeconds: number;
}

interface MensagemResponse {
  mensagem: string;
}

// ── Auth Session (o que o AuthContext expõe como `user`) ──────────────────────

export interface AuthSession {
  uid: string;
  email: string;
  nome: string;
}

export function sessaoDoPerfil(perfil: PerfilUsuario): AuthSession {
  return { uid: perfil.id, email: perfil.email, nome: perfil.nome };
}

// ── me ────────────────────────────────────────────────────────────────────────

export function meApi(): Promise<PerfilUsuario> {
  return apiGet<PerfilUsuario>('/auth/me');
}

// ── login ─────────────────────────────────────────────────────────────────────

export async function loginApi(email: string, senha: string): Promise<AuthSession> {
  const data = await apiPost<TokenResponse>('/auth/login', { email, senha }, { public: true });
  saveTokens(data.accessToken, data.refreshToken);

  // Obtém dados do usuário autenticado para montar a sessão
  const me = await meApi();
  if (me.tipoPessoa !== 'PROPRIETARIO') {
    // O front-app é do proprietário; admin usa o painel do front-site.
    clearTokens();
    const erro: ApiError = {
      status: 403,
      message: 'Esta conta não é de proprietário. Use o painel administrativo.',
      code: 'auth/not-proprietario',
      fieldErrors: {},
    };
    throw erro;
  }
  return sessaoDoPerfil(me);
}

// ── cadastrar ─────────────────────────────────────────────────────────────────

export async function cadastrarApi(payload: ProprietarioCadastroRequest): Promise<PerfilUsuario> {
  return apiPost<PerfilUsuario>('/auth/cadastrar', payload, { public: true });
}

// ── recuperar / redefinir senha ───────────────────────────────────────────────

export async function recuperarSenhaApi(email: string): Promise<void> {
  await apiPost<MensagemResponse>('/auth/recuperar-senha', { email }, { public: true });
}

export async function redefinirSenhaApi(token: string, novaSenha: string): Promise<void> {
  await apiPost<MensagemResponse>('/auth/redefinir-senha', { token, novaSenha }, { public: true });
}

// ── perfil do proprietário ────────────────────────────────────────────────────

export function atualizarProprietarioApi(id: string, dados: PessoaUpdateRequest): Promise<Proprietario> {
  return apiPut<Proprietario>(`/api/proprietarios/${id}`, dados);
}

export async function alterarSenhaApi(senhaAtual: string, novaSenha: string): Promise<void> {
  await apiPost<MensagemResponse>('/auth/alterar-senha', { senhaAtual, novaSenha });
}

/**
 * Exclusão de conta pelo próprio titular (LGPD).
 *
 * O backend remove os dados operacionais antes do cadastro porque as chaves
 * estrangeiras usam ON DELETE RESTRICT. Os produtos continuam no catálogo
 * compartilhado; o que sai é o estoque da pessoa.
 */
export async function excluirContaApi(id: string): Promise<void> {
  await apiDelete<void>(`/api/pessoas/${id}/conta`);
}

// ── logout (limpa tokens localmente) ─────────────────────────────────────────

export function logoutApi(): void {
  clearTokens();
}
