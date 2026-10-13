// Só no servidor (rotas de /api): o navegador fala com o Next, e o Next com o backend.
export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

/** Repassa a resposta do backend (status e corpo) para o navegador. */
export async function repassar(res: Response): Promise<Response> {
  const corpo = await res.text();
  const headers: Record<string, string> = {
    "Content-Type": res.headers.get("Content-Type") ?? "application/json",
  };
  // 429 do limite de pedidos: diz em quantos segundos tentar de novo
  const retryAfter = res.headers.get("Retry-After");
  if (retryAfter) headers["Retry-After"] = retryAfter;
  return new Response(corpo, { status: res.status, headers });
}

export function backendIndisponivel(): Response {
  return Response.json(
    { message: "Não foi possível falar com o servidor. Tente novamente em instantes." },
    { status: 502 }
  );
}
