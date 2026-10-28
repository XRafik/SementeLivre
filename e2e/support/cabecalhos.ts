import { APIResponse, Page, expect } from '@playwright/test';

/** Cabeçalhos de segurança que as páginas dos dois frontends mandam (#202). */
export function conferirCabecalhosDeSeguranca(res: APIResponse) {
  const h = res.headers();
  expect(h['content-security-policy']).toContain("default-src 'self'");
  expect(h['content-security-policy']).toContain("frame-ancestors 'none'");
  expect(h['content-security-policy']).toContain("object-src 'none'");
  expect(h['x-frame-options']).toBe('DENY');
  expect(h['x-content-type-options']).toBe('nosniff');
  expect(h['referrer-policy']).toBe('strict-origin-when-cross-origin');
  expect(h['strict-transport-security']).toContain('max-age=');
  expect(h['permissions-policy']).toContain('microphone=()');
  expect(h['x-powered-by']).toBeUndefined();
}

/** Junta as violações da CSP que o navegador reporta no console. */
export function violacoesDaCsp(page: Page): string[] {
  const violacoes: string[] = [];
  page.on('console', (msg) => {
    if (/Content Security Policy/i.test(msg.text())) violacoes.push(msg.text());
  });
  return violacoes;
}
