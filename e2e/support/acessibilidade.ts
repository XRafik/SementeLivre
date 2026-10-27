import AxeBuilder from '@axe-core/playwright';
import { Page, expect } from '@playwright/test';

/**
 * Varredura WCAG 2.1 A/AA com o axe (#182). Falha em violações "serious" e
 * "critical"; as menores aparecem no relatório do teste, sem derrubar.
 */
export async function semViolacoesGraves(page: Page, tela: string) {
  // Espera a tela assentar (splash, carregamentos) antes de varrer
  await page.waitForLoadState('networkidle');

  const { violations } = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
    .analyze();

  const resumo = violations.map((v) => ({
    regra: v.id,
    impacto: v.impact,
    ajuda: v.help,
    elementos: v.nodes.slice(0, 3).map((n) => n.target.join(' ')),
  }));
  if (resumo.length > 0) {
    test_info_anexar(tela, resumo);
  }

  const graves = resumo.filter((v) => v.impacto === 'serious' || v.impacto === 'critical');
  // soft: uma tela com problema não esconde as outras do mesmo teste
  expect.soft(graves, `${tela}: ${JSON.stringify(graves, null, 2)}`).toEqual([]);
}

function test_info_anexar(tela: string, resumo: unknown) {
  console.log(`[axe] ${tela}: ${JSON.stringify(resumo)}`);
}
