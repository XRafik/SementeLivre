import { BACKEND_URL, backendIndisponivel, repassar } from "@/lib/backend";

// Registra o pedido no backend, que também envia a cópia por e-mail.
export async function POST(request: Request) {
  const body = await request.text();
  // O backend limita pedidos por IP; sem repassar, todo visitante teria o IP deste servidor
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  const ipDoVisitante = request.headers.get("x-forwarded-for");
  if (ipDoVisitante) headers["X-Forwarded-For"] = ipDoVisitante;
  try {
    const res = await fetch(`${BACKEND_URL}/catalogo/pedidos`, {
      method: "POST",
      headers,
      body,
    });
    return repassar(res);
  } catch {
    return backendIndisponivel();
  }
}
