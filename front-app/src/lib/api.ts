/**
 * api.ts — Cliente HTTP centralizado para o backend REST (Spring Boot).
 *
 * Responsabilidades:
 *  - Anexar o accessToken JWT em cada requisição autenticada.
 *  - Tentar renovar o accessToken via /auth/refresh quando receber 401.
 *  - Persistir / limpar tokens no localStorage de forma padronizada.
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

// ── Chaves de armazenamento ────────────────────────────────────────────────────

const TOKEN_KEY = 'sl_access_token';
const REFRESH_KEY = 'sl_refresh_token';

// ── Persistência de tokens ─────────────────────────────────────────────────────

export function getAccessToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(TOKEN_KEY);
}

export function getRefreshToken(): string | null {
  if (typeof window === 'undefined') return null;
  return localStorage.getItem(REFRESH_KEY);
}

export function saveTokens(accessToken: string, refreshToken: string): void {
  localStorage.setItem(TOKEN_KEY, accessToken);
  localStorage.setItem(REFRESH_KEY, refreshToken);
}

export function clearTokens(): void {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_KEY);
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

// ── Refresh silencioso ─────────────────────────────────────────────────────────

let _refreshPromise: Promise<string | null> | null = null;

async function silentRefresh(): Promise<string | null> {
  // Evitar múltiplas chamadas simultâneas de refresh
  if (_refreshPromise) return _refreshPromise;

  _refreshPromise = (async () => {
    const refreshToken = getRefreshToken();
    if (!refreshToken) return null;

    try {
      // fetch direto (e não request()) para um 401/400 do próprio refresh nunca disparar outro refresh
      const res = await fetch(`${BASE_URL}/auth/refresh`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken }),
      });

      if (!res.ok) {
        clearTokens();
        return null;
      }

      const data = await res.json();
      saveTokens(data.accessToken, data.refreshToken ?? refreshToken);
      return data.accessToken as string;
    } catch {
      clearTokens();
      return null;
    } finally {
      _refreshPromise = null;
    }
  })();

  return _refreshPromise;
}

/** Renova o accessToken sem novo login. Usado quando o token atual deixou de valer (ex.: troca de e-mail). */
export async function renovarToken(): Promise<boolean> {
  return (await silentRefresh()) !== null;
}

// 401 nessas rotas é resposta do próprio fluxo de auth (credencial errada, refresh inválido), não token vencido.
const ROTAS_SEM_REFRESH = ['/auth/login', '/auth/refresh', '/auth/cadastrar'];

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
    const newToken = await silentRefresh();
    if (!newToken) {
      clearTokens();
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event('sl:session-expired'));
      }
      emitirToast(MENSAGEM_SESSAO_EXPIRADA, 'warning');
      throw buildApiError(401, { message: MENSAGEM_SESSAO_EXPIRADA, code: 'auth/session-expired' });
    }
    token = newToken;
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
    const newToken = await silentRefresh();
    if (!newToken) {
      clearTokens();
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event('sl:session-expired'));
      }
      emitirToast(MENSAGEM_SESSAO_EXPIRADA, 'warning');
      throw buildApiError(401, { message: MENSAGEM_SESSAO_EXPIRADA, code: 'auth/session-expired' });
    }
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
