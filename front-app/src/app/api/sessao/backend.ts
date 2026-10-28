import { NextRequest, NextResponse } from 'next/server';

/**
 * Sessão do app (#201). O refresh token não passa mais pelo JavaScript da
 * página: estas rotas, no servidor do próprio front-app, falam com o backend e
 * guardam o refresh num cookie HttpOnly. A página só recebe o access token,
 * que fica na memória (lib/api.ts).
 */

export const COOKIE_REFRESH = 'sl_refresh';

// O cookie só vai para as rotas de sessão, não para cada página e imagem do app
const CAMINHO_DO_COOKIE = '/api/sessao';

// Mesma validade do refresh token no backend (jwt.refresh-expiration-days)
const SETE_DIAS = 7 * 24 * 60 * 60;

const BACKEND_URL = process.env.BACKEND_URL ?? process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

/** POST no backend. Devolve null quando o backend não responde. */
export async function chamarBackend(request: NextRequest, caminho: string, corpo: unknown): Promise<Response | null> {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  // O backend limita login e refresh por IP (#200); sem repassar, todo mundo teria o IP deste servidor
  const ipDoUsuario = request.headers.get('x-forwarded-for');
  if (ipDoUsuario) headers['X-Forwarded-For'] = ipDoUsuario;
  try {
    return await fetch(`${BACKEND_URL}${caminho}`, {
      method: 'POST',
      headers,
      body: JSON.stringify(corpo),
      cache: 'no-store',
    });
  } catch {
    return null;
  }
}

export function backendIndisponivel(): NextResponse {
  return NextResponse.json({ message: 'Servidor indisponível.', code: 'network/offline' }, { status: 503 });
}

/** Repassa o erro do backend (status, corpo e Retry-After do 429). */
export async function repassarErro(res: Response): Promise<NextResponse> {
  const headers: Record<string, string> = {
    'Content-Type': res.headers.get('content-type') ?? 'application/json',
  };
  const retryAfter = res.headers.get('retry-after');
  if (retryAfter) headers['Retry-After'] = retryAfter;
  return new NextResponse(await res.text(), { status: res.status, headers });
}

/** Responde o access token e grava o refresh token no cookie. */
export function respostaComSessao(
  request: NextRequest,
  tokens: { accessToken: string; refreshToken: string; expiresInSeconds?: number },
): NextResponse {
  const resposta = NextResponse.json(
    { accessToken: tokens.accessToken, expiresInSeconds: tokens.expiresInSeconds },
    { headers: { 'Cache-Control': 'no-store' } },
  );
  resposta.cookies.set(COOKIE_REFRESH, tokens.refreshToken, {
    httpOnly: true,
    sameSite: 'strict',
    secure: emHttps(request),
    path: CAMINHO_DO_COOKIE,
    maxAge: SETE_DIAS,
  });
  return resposta;
}

export function apagarCookie(request: NextRequest, resposta: NextResponse): NextResponse {
  resposta.cookies.set(COOKIE_REFRESH, '', {
    httpOnly: true,
    sameSite: 'strict',
    secure: emHttps(request),
    path: CAMINHO_DO_COOKIE,
    maxAge: 0,
  });
  return resposta;
}

// Atrás de um proxy com TLS (Caddy), o Next recebe http e o proxy avisa no X-Forwarded-Proto
function emHttps(request: NextRequest): boolean {
  return request.nextUrl.protocol === 'https:' || request.headers.get('x-forwarded-proto') === 'https';
}
