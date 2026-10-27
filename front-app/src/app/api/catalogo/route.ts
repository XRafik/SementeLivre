import { NextRequest, NextResponse } from 'next/server';

const backend = process.env.BACKEND_URL ?? 'http://localhost:8080';

export async function GET(request: NextRequest) {
  try {
    const backendUrl = process.env.BACKEND_URL;

    if (!backendUrl) {
      return NextResponse.json(
        { error: 'BACKEND_URL não configurada.' },
        { status: 500 }
      );
    }

    const params = new URLSearchParams(request.nextUrl.searchParams);

    const response = await fetch(
      `${backendUrl}/catalogo/produtos?${params.toString()}`,
      {
        cache: 'no-store',
      }
    );

    if (!response.ok) {
      return NextResponse.json(
        { error: 'Erro ao consultar o catálogo no backend.' },
        { status: response.status }
      );
    }

    const data = await response.json();

    // O backend guarda o caminho relativo da foto (/uploads/produtos/...).
    // Sem prefixar a origem, o <img> resolve contra o Next (:3000), que não
    // serve /uploads, e a imagem quebra com 404.
    return NextResponse.json(comUrlDeFotoAbsoluta(data));
  } catch {
    return NextResponse.json(
      { error: 'Não foi possível conectar ao backend.' },
      { status: 502 }
    );
  }
}

function comUrlDeFotoAbsoluta(data: unknown): unknown {
  if (Array.isArray(data)) return data.map(comUrlDeFotoAbsoluta);
  if (data && typeof data === 'object') {
    const registro = data as Record<string, unknown>;
    const conteudo = registro.content;
    if (Array.isArray(conteudo)) {
      return { ...registro, content: conteudo.map(comUrlDeFotoAbsoluta) };
    }
    if (typeof registro.urlFoto === 'string' && registro.urlFoto.startsWith('/uploads/')) {
      return { ...registro, urlFoto: `${backend}${registro.urlFoto}` };
    }
  }
  return data;
}