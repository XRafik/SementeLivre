/**
 * api.ts — Cliente HTTP centralizado para o backend REST (Spring Boot).
 *
 * Responsabilidades:
 *  - Anexar o accessToken JWT em cada requisição autenticada.
 *  - Tentar renovar o accessToken pela sessão (/api/sessao/renovar) quando receber 401.
 *  - Guardar o accessToken (30 min) na sessionStorage da aba; o refresh token
 *    (7 dias) fica num cookie HttpOnly das rotas /api/sessao, que script nenhum
 *    da página lê (#201).
 *  - Avisar por toast quando não há conexão ou quando a sessão expira.
 *  - Exportar helpers tipados: apiGet, apiPost, apiPut, apiPatch, apiDelete.
 */

import { emitirToast } from './toast';

const BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export { BASE_URL };

/**
 * Converte um caminho relativo devolvido pelo backend em URL absoluta.
 * O /produtos/upload-foto responde "/uploads/produtos/abc.png"; gravamos esse
 * caminho relativo no banco (para não amarrar a origem) e resolvemos na leitura.
 */
export function apiUrl(caminho: string | null | undefined): string {
  if (!caminho) return '';
  if (/^https?:\/\//i.test(caminho) || caminho.startsWith('data:')) return caminho;
  return `${BASE_URL}${caminho.startsWith('/') ? '' : '/'}${caminho}`;
}

// ── Tokens ─────────────────────────────────────────────────────────────────────

// O access token fica na sessionStorage da aba, e não só na memória: assim recarregar
// ou abrir outra tela não renova a sessão. Cada renovação troca o refresh token, e uma
// renovação cortada no meio por uma navegação deixava o navegador com o refresh
// antigo, já revogado (a sessão caía). Um script injetado na página conseguiria um
// access token de qualquer jeito, pedindo a /api/sessao/renovar; o que não pode
// vazar é o refresh, que fica no cookie.
const CHAVE_ACCESS = 'sl_access';

// Versões antigas guardavam os dois tokens no localStorage
const CHAVES_ANTIGAS = ['sl_access_token', 'sl_refresh_token'];

let accessToken: string | null = null;

function guardarNaAba(token: string | null): void {
  try {
    if (token) sessionStorage.setItem(CHAVE_ACCESS, token);
    else sessionStorage.removeItem(CHAVE_ACCESS);
  } catch {
    // sem sessionStorage (modo privado restrito): fica só na memória
  }
}

export function getAccessToken(): string | null {
  if (accessToken || typeof window === 'undefined') return accessToken;
  try {
    accessToken = sessionStorage.getItem(CHAVE_ACCESS);
  } catch {
    // sem sessionStorage
  }
  return accessToken;
}

export function saveAccessToken(token: string): void {
  accessToken = token;
  guardarNaAba(token);
}

export function clearTokens(): void {
  accessToken = null;
  guardarNaAba(null);
  try {
    CHAVES_ANTIGAS.forEach((chave) => localStorage.removeItem(chave));
  } catch {
    // sem localStorage (modo privado restrito): nada a limpar
  }
}

// ── Tipos auxiliares ───────────────────────────────────────────────────────────

export interface ApiError {
  /** Status HTTP; 0 quando não houve resposta (sem conexão). */
  status: number;
  message: string;
  /** Código semântico de erro, ex: "auth/session-expired" */
  code?: string;
  /** Erros por campo do DTO (ex.: "email", "endereco.uf"), vindos de fieldErrors: ["campo: mensagem"]. */
  fieldErrors: Record<string, string>;
}

export function isApiError(err: unknown): err is ApiError {
  return !!err && typeof err === 'object' && typeof (err as ApiError).status === 'number';
}

export const MENSAGEM_SEM_CONEXAO = 'Sem conexão com o servidor. Verifique sua internet e tente novamente.';
export const MENSAGEM_SESSAO_EXPIRADA = 'Sua sessão expirou. Faça login novamente.';

function buildApiError(status: number, body: unknown): ApiError {
  if (body && typeof body === 'object') {
    const b = body as Record<string, unknown>;
    const fieldErrors: Record<string, string> = {};
    for (const item of (b.fieldErrors as string[] | null | undefined) ?? []) {
      const separador = item.indexOf(': ');
      if (separador > 0) {
        fieldErrors[item.slice(0, separador)] ??= item.slice(separador + 2);
      }
    }
    return {
      status,
      message: (b.mensagem ?? b.message ?? b.error ?? 'Erro desconhecido') as string,
      code: b.code as string | undefined,
      fieldErrors,
    };
  }
  return { status, message: String(body || 'Erro desconhecido'), fieldErrors: {} };
}

// ── Sessão (rotas /api/sessao do próprio app) ──────────────────────────────────

/**
 * Chama uma rota de sessão do app. Erros viram ApiError como os do backend;
 * sem conexão (ou backend fora do ar, 503) avisa por toast e lança status 0.
 */
export async function chamarSessao<T>(caminho: string, metodo: 'POST' | 'DELETE', corpo?: unknown): Promise<T> {
  let res: Response;
  try {
    res = await fetch(`/api/sessao${caminho}`, {
      method: metodo,
      headers: corpo !== undefined ? { 'Content-Type': 'application/json' } : undefined,
      body: corpo !== undefined ? JSON.stringify(corpo) : undefined,
    });
  } catch {
    res = new Response(null, { status: 503 });
  }

  if (res.status === 503) {
    emitirToast(MENSAGEM_SEM_CONEXAO, 'error');
    throw buildApiError(0, { message: MENSAGEM_SEM_CONEXAO, code: 'network/offline' });
  }
  if (!res.ok) {
    const texto = await res.text();
    let corpoDoErro: unknown = texto;
    try {
      corpoDoErro = JSON.parse(texto);
    } catch {
      // corpo vazio ou não-JSON
    }
    throw buildApiError(res.status, corpoDoErro);
  }
  if (res.status === 204) return undefined as unknown as T;
  return res.json() as Promise<T>;
}

/** 'ok': sessão renovada; 'sem-sessao': não há (ou acabou); 'indisponivel': servidor fora do ar ou ocupado. */
export type Renovacao = 'ok' | 'sem-sessao' | 'indisponivel';

let _renovacao: Promise<Renovacao> | null = null;

async function pedirRenovacao(): Promise<Renovacao> {
  try {
    // fetch direto (e não request()) para um 401 da própria renovação nunca disparar outra
    const res = await fetch('/api/sessao/renovar', { method: 'POST' });
    if (res.ok) {
      saveAccessToken(((await res.json()) as { accessToken: string }).accessToken);
      return 'ok';
    }
    if (res.status === 503 || res.status === 429) return 'indisponivel';
    clearTokens();
    return 'sem-sessao';
  } catch {
    return 'indisponivel';
  }
}

/** Troca o refresh token do cookie por um access token novo. */
export function renovarSessao(): Promise<Renovacao> {
  // Uma renovação por vez nesta aba
  if (_renovacao) return _renovacao;

  _renovacao = (async () => {
    try {
      // ...e entre abas: elas dividem o cookie, e o refresh troca a cada uso. Duas
      // renovações juntas mandariam o mesmo refresh e a segunda derrubaria a sessão.
      const locks = typeof navigator !== 'undefined' ? navigator.locks : undefined;
      return locks ? await locks.request('sl-renovar-sessao', pedirRenovacao) : await pedirRenovacao();
    } finally {
      _renovacao = null;
    }
  })();

  return _renovacao;
}

/** Renova o accessToken sem novo login. Usado quando o token atual deixou de valer (ex.: troca de e-mail). */
export async function renovarToken(): Promise<boolean> {
  return (await renovarSessao()) === 'ok';
}

/** Há sessão? Sem access token na aba (aba nova), tenta renová-la pelo cookie. */
export async function verificarSessao(): Promise<Renovacao> {
  return getAccessToken() ? 'ok' : renovarSessao();
}

/** Depois de um 401: renova e devolve o token novo, ou lança o erro de sessão expirada / sem conexão. */
async function renovarDepoisDe401(): Promise<string> {
  const renovacao = await renovarSessao();
  if (renovacao === 'indisponivel') {
    emitirToast(MENSAGEM_SEM_CONEXAO, 'error');
    throw buildApiError(0, { message: MENSAGEM_SEM_CONEXAO, code: 'network/offline' });
  }
  if (renovacao === 'sem-sessao' || !getAccessToken()) {
    clearTokens();
    if (typeof window !== 'undefined') {
      window.dispatchEvent(new Event('sl:session-expired'));
    }
    emitirToast(MENSAGEM_SESSAO_EXPIRADA, 'warning');
    throw buildApiError(401, { message: MENSAGEM_SESSAO_EXPIRADA, code: 'auth/session-expired' });
  }
  return getAccessToken()!;
}

// 401 nessas rotas é resposta do próprio fluxo de auth (credencial errada), não token vencido.
const ROTAS_SEM_REFRESH = ['/auth/login', '/auth/cadastrar'];

// ── Fetch base ─────────────────────────────────────────────────────────────────

interface RequestOptions extends Omit<RequestInit, 'body'> {
  body?: unknown;
  /** Se true, não envia o header Authorization (para rotas públicas) */
  public?: boolean;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { body, public: isPublic, headers: extraHeaders, ...rest } = options;

  const buildHeaders = (token: string | null): HeadersInit => {
    const h: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(extraHeaders as Record<string, string>),
    };
    if (!isPublic && token) {
      h['Authorization'] = `Bearer ${token}`;
    }
    return h;
  };

  const doFetch = async (token: string | null) => {
    try {
      return await fetch(`${BASE_URL}${path}`, {
        ...rest,
        headers: buildHeaders(token),
        body: body !== undefined ? JSON.stringify(body) : undefined,
      });
    } catch {
      emitirToast(MENSAGEM_SEM_CONEXAO, 'error');
      throw buildApiError(0, { message: MENSAGEM_SEM_CONEXAO, code: 'network/offline' });
    }
  };

  let token = getAccessToken();
  let res = await doFetch(token);

  // ── Se 401, tenta refresh uma vez ──────────────────────────────────────────
  const podeRenovar = !isPublic && !ROTAS_SEM_REFRESH.some((rota) => path.startsWith(rota));
  if (res.status === 401 && podeRenovar) {
    token = await renovarDepoisDe401();
    res = await doFetch(token);
  }

  // ── Tratar erros HTTP ──────────────────────────────────────────────────────
  if (!res.ok) {
    const texto = await res.text();
    let errorBody: unknown = texto;
    try {
      errorBody = JSON.parse(texto);
    } catch {
      // corpo vazio ou não-JSON (ex.: 401 do login)
    }
    throw buildApiError(res.status, errorBody);
  }

  if (res.status === 204) return undefined as unknown as T;

  return res.json() as Promise<T>;
}

// ── Helpers públicos ───────────────────────────────────────────────────────────

export function apiGet<T>(path: string, options?: RequestOptions): Promise<T> {
  return request<T>(path, { ...options, method: 'GET' });
}

export function apiPost<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  return request<T>(path, { ...options, method: 'POST', body });
}

export function apiPut<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  return request<T>(path, { ...options, method: 'PUT', body });
}

export function apiPatch<T>(path: string, body?: unknown, options?: RequestOptions): Promise<T> {
  return request<T>(path, { ...options, method: 'PATCH', body });
}

export function apiDelete<T>(path: string, options?: RequestOptions): Promise<T> {
  return request<T>(path, { ...options, method: 'DELETE' });
}

/**
 * Envia FormData (multipart) com o accessToken e renova o token em caso de 401.
 * Não define Content-Type: o próprio browser acrescenta o boundary do multipart.
 */
export async function apiUpload<T>(path: string, formData: FormData): Promise<T> {
  const doFetch = (token: string | null, dados: FormData) =>
    fetch(`${BASE_URL}${path}`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: dados,
    }).catch(() => {
      emitirToast(MENSAGEM_SEM_CONEXAO, 'error');
      throw buildApiError(0, { message: MENSAGEM_SEM_CONEXAO, code: 'network/offline' });
    });

  let res = await doFetch(getAccessToken(), formData);

  if (res.status === 401) {
    const newToken = await renovarDepoisDe401();
    // O FormData só pode ser lido uma vez: recria os campos para o reenvio.
    const reenvio = new FormData();
    formData.forEach((valor, chave) => reenvio.append(chave, valor));
    res = await doFetch(newToken, reenvio);
  }

  if (!res.ok) {
    const texto = await res.text();
    let body: unknown = texto;
    try {
      body = JSON.parse(texto);
    } catch {
      // resposta não-JSON
    }
    throw buildApiError(res.status, body);
  }

  if (res.status === 204) return undefined as unknown as T;

  // O /produtos/upload-foto devolve uma String crua (text/plain), não JSON.
  // Tentamos o parse e, se não for JSON, devolvemos o texto como está.
  const texto = (await res.text()).trim();
  if (!texto) return undefined as unknown as T;
  try {
    return JSON.parse(texto) as T;
  } catch {
    return texto as unknown as T;
  }
}

/** Anexa os parâmetros preenchidos à rota: comQuery('/pedidos', { status: 'PENDENTE', tipo: undefined }). */
export function comQuery(path: string, query: Record<string, string | number | null | undefined>): string {
  const params = new URLSearchParams();
  Object.entries(query).forEach(([chave, valor]) => {
    if (valor !== undefined && valor !== null && valor !== '') params.set(chave, String(valor));
  });
  const qs = params.toString();
  return qs ? `${path}?${qs}` : path;
}

/** Mensagem para a tela: a do backend quando houver, senão o texto padrão. */
export function errorMessage(err: unknown, fallback: string): string {
  if (isApiError(err) && err.message) return err.message;
  if (err instanceof Error && err.message) return err.message;
  return fallback;
}
