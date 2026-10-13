import { BACKEND_URL, backendIndisponivel, repassar } from "@/lib/backend";

// Registra o pedido no backend, que também envia a cópia por e-mail.
export async function POST(request: Request) {
  const body = await request.text();
  try {
    const res = await fetch(`${BACKEND_URL}/catalogo/pedidos`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body,
    });
    return repassar(res);
  } catch {
    return backendIndisponivel();
  }
}
