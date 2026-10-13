import { BACKEND_URL, backendIndisponivel } from "@/lib/backend";

// Catálogo público do backend. O site lista tudo de uma vez e filtra no navegador.
export async function GET() {
  try {
    const res = await fetch(`${BACKEND_URL}/catalogo/produtos?size=200`, {
      cache: "no-store",
    });
    if (!res.ok) return backendIndisponivel();
    const pagina = await res.json();
    return Response.json(pagina.content ?? []);
  } catch {
    return backendIndisponivel();
  }
}
