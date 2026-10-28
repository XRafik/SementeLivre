import { NextRequest, NextResponse } from 'next/server';
import { COOKIE_REFRESH, apagarCookie, backendIndisponivel, chamarBackend, repassarErro, respostaComSessao } from '../backend';

/**
 * Renova a sessão com o refresh token do cookie: POST /api/sessao/renovar → { accessToken }.
 * 401 quando não há sessão; 503 quando o backend não responde (a página
 * mantém a sessão e oferece tentar de novo).
 */
export async function POST(request: NextRequest) {
  const refreshToken = request.cookies.get(COOKIE_REFRESH)?.value;
  if (!refreshToken) {
    return NextResponse.json({ message: 'Sem sessão.', code: 'auth/sem-sessao' }, { status: 401 });
  }

  const res = await chamarBackend(request, '/auth/refresh', { refreshToken });
  if (!res || res.status >= 500) return backendIndisponivel();
  // Limite de renovações (#200): a sessão continua, só precisa esperar
  if (res.status === 429) return repassarErro(res);
  if (!res.ok) {
    // Refresh vencido, revogado ou trocado em outro aparelho: a sessão acabou
    return apagarCookie(
      request,
      NextResponse.json({ message: 'Sessão expirada.', code: 'auth/session-expired' }, { status: 401 }),
    );
  }

  return respostaComSessao(request, await res.json());
}
