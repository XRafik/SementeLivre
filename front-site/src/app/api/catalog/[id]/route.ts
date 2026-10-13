import { BACKEND_URL, backendIndisponivel, repassar } from "@/lib/backend";

// Produto do catálogo com todas as ofertas (um estoque por produtor).
export async function GET(
  _request: Request,
  { params }: { params: Promise<{ id: string }> }
) {
  const { id } = await params;
  try {
    const res = await fetch(`${BACKEND_URL}/catalogo/produtos/${encodeURIComponent(id)}`, {
      cache: "no-store",
    });
    return repassar(res);
  } catch {
    return backendIndisponivel();
  }
}
