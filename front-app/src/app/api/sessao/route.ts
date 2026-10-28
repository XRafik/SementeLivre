import { NextRequest, NextResponse } from 'next/server';
import {
  COOKIE_REFRESH,
  apagarCookie,
  backendIndisponivel,
  chamarBackend,
  repassarErro,
  respostaComSessao,
} from './backend';

/** Login: POST /api/sessao { email, senha } → { accessToken }, com o refresh no cookie. */
export async function POST(request: NextRequest) {
  let credenciais: unknown;
  try {
    credenciais = await request.json();
  } catch {
    return NextResponse.json({ message: 'Corpo inválido.' }, { status: 400 });
  }

  const res = await chamarBackend(request, '/auth/login', credenciais);
  if (!res) return backendIndisponivel();
  if (!res.ok) return repassarErro(res);

  return respostaComSessao(request, await res.json());
}

/** Sair: revoga o refresh token no backend e apaga o cookie. */
export async function DELETE(request: NextRequest) {
  const refreshToken = request.cookies.get(COOKIE_REFRESH)?.value;
  if (refreshToken) {
    // Backend fora do ar não impede sair deste aparelho: o cookie sai de qualquer jeito
    await chamarBackend(request, '/auth/sair', { refreshToken });
  }
  return apagarCookie(request, new NextResponse(null, { status: 204 }));
}
