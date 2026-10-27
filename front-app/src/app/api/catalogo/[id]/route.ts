import { NextRequest, NextResponse } from 'next/server';

export async function GET(
  _request: NextRequest,
  context: { params: Promise<{ id: string }> }
) {
  try {
    const backendUrl = process.env.BACKEND_URL;

    if (!backendUrl) {
      return NextResponse.json(
        { error: 'BACKEND_URL não configurada.' },
        { status: 500 }
      );
    }

    const { id } = await context.params;

    const response = await fetch(
      `${backendUrl}/catalogo/produtos/${id}`,
      {
        cache: 'no-store',
      }
    );

    if (!response.ok) {
      return NextResponse.json(
        { error: 'Produto não encontrado no catálogo.' },
        { status: response.status }
      );
    }

    const data = await response.json();

    // Ver comentário em ../route.ts: o caminho da foto precisa da origem do backend.
    const urlFoto =
      typeof data?.urlFoto === 'string' && data.urlFoto.startsWith('/uploads/')
        ? `${backendUrl}${data.urlFoto}`
        : data?.urlFoto;

    return NextResponse.json({ ...data, urlFoto });
  } catch {
    return NextResponse.json(
      { error: 'Não foi possível conectar ao backend.' },
      { status: 502 }
    );
  }
}