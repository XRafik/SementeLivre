/**
 * Logos oficiais do IF Sudeste MG, Campus Rio Pomba, em public/logos/.
 * Medidas em px dos próprios PNGs (já com a margem transparente do arquivo), usadas para
 * aplicar o manual da marca: símbolo com no mínimo 30px de largura e área livre de 1 módulo,
 * que é a largura de um quadradinho verde.
 */
export const LOGOS_IF = {
  vertical: { src: '/logos/logo_vertical_riopomba-2.png', largura: 1104, altura: 1698, simboloLargura: 431, modulo: 125 },
  horizontal: { src: '/logos/logo_horizontal_riopomba-3.png', largura: 1698, altura: 510, simboloLargura: 308, modulo: 89 },
  divisoria: { src: '/logos/logo_horizontal_riopomba-1.png', largura: 2316, altura: 510, simboloLargura: 306, modulo: 89 },
} as const;

export type VarianteLogoIF = keyof typeof LOGOS_IF;

export const SIMBOLO_MIN_PX = 30;

export const ALT_LOGO_IF = 'Instituto Federal do Sudeste de Minas Gerais, Campus Rio Pomba';

export const CREDITO_IF =
  'Projeto desenvolvido pela turma de Ciência da Computação na disciplina AAIFE IV (Ação de Extensão Aplicada à Ciência da Computação), IF Sudeste MG, Campus Rio Pomba.';
