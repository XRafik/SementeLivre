// Só no servidor (rotas de /api): o navegador fala com o Next, e o Next com o backend.
export const BACKEND_URL = process.env.BACKEND_URL ?? "http://localhost:8080";

/** Repassa a resposta do backend (status e corpo) para o navegador. */
export async function repassar(res: Response): Promise<Response> {
  const corpo = await res.text();
  return new Response(corpo, {
    status: res.status,
    headers: { "Content-Type": res.headers.get("Content-Type") ?? "application/json" },
  });
}

export function backendIndisponivel(): Response {
  return Response.json(
    { message: "Não foi possível falar com o servidor. Tente novamente em instantes." },
    { status: 502 }
  );
}
