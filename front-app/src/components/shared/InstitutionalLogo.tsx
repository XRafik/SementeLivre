import clsx from 'clsx';
import { ALT_LOGO_IF, CREDITO_IF, LOGOS_IF, SIMBOLO_MIN_PX, type VarianteLogoIF } from '@/lib/institutionalLogo';
import styles from './InstitutionalLogo.module.css';

interface InstitutionalLogoProps {
  variant?: VarianteLogoIF;
  /**
   * Altura da imagem em px (não em rem, para o zoom de texto não encolher o símbolo).
   * Valores que deixariam o símbolo abaixo de 30px sobem para o mínimo.
   */
  height?: number;
  /** Base branca, obrigatória sobre fundo colorido, escuro ou com imagem. */
  card?: boolean;
  /** Crédito da disciplina abaixo da logo, fora da área livre. */
  credit?: boolean;
  align?: 'center' | 'start';
  className?: string;
}

/**
 * Logo do IF Sudeste MG, Campus Rio Pomba, como identificação institucional secundária.
 * Concentra as regras do manual da marca: proporção do arquivo, símbolo com no mínimo 30px,
 * área livre de 1 módulo e nenhum efeito (sombra, opacidade, filtro, rotação ou animação).
 */
export function InstitutionalLogo({
  variant = 'horizontal',
  height = 56,
  card = false,
  credit = false,
  align = 'center',
  className,
}: InstitutionalLogoProps) {
  const logo = LOGOS_IF[variant];
  const alturaMinima = Math.ceil((SIMBOLO_MIN_PX * logo.altura) / logo.simboloLargura);
  const altura = Math.max(height, alturaMinima);
  const largura = Math.round((altura * logo.largura) / logo.altura);
  const modulo = Math.ceil((altura * logo.modulo) / logo.altura);

  return (
    <div className={clsx(styles.wrapper, align === 'start' && styles.start, className)}>
      {/* O padding de 1 módulo é a área livre: nenhum outro elemento entra nele. */}
      <div className={clsx(styles.area, card && styles.card)} style={{ padding: modulo }}>
        <img
          src={logo.src}
          alt={ALT_LOGO_IF}
          width={largura}
          height={altura}
          className={styles.logo}
          draggable={false}
        />
      </div>
      {credit && <p className={styles.credit}>{CREDITO_IF}</p>}
    </div>
  );
}
