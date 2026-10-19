import { request } from '@playwright/test';
import { API_URL } from './api';

/** Falha cedo, com mensagem clara, se o backend real não estiver no ar. */
export default async function globalSetup() {
  const api = await request.newContext({ baseURL: API_URL });
  try {
    const res = await api.get('/actuator/health');
    if (!res.ok()) throw new Error(`status ${res.status()}`);
  } catch (err) {
    throw new Error(
      `Backend fora do ar em ${API_URL} (${(err as Error).message}). Suba o backend antes dos testes: veja e2e/README.md.`
    );
  } finally {
    await api.dispose();
  }
}
