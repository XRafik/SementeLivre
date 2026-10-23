'use client';

import { useEffect, useState } from 'react';
import { InstitutionalLogo } from './InstitutionalLogo';
import styles from './SplashScreen.module.css';

/**
 * Tela splash exibida uma única vez a cada abertura da aplicação (page load),
 * cobrindo tanto o grupo (auth) quanto o (app). Navegações internas não
 * remontam o layout raiz, então ela não aparece novamente entre telas.
 */
const DURACAO_MS = 2000;
const TRANSICAO_MS = 400;

export function SplashScreen() {
  const [visivel, setVisivel] = useState(true);
  const [saindo, setSaindo] = useState(false);

  useEffect(() => {
    const comecarSaida = setTimeout(() => setSaindo(true), DURACAO_MS);
    const desmontar = setTimeout(() => setVisivel(false), DURACAO_MS + TRANSICAO_MS);
    return () => {
      clearTimeout(comecarSaida);
      clearTimeout(desmontar);
    };
  }, []);

  if (!visivel) return null;

  return (
    <div
      className={`${styles.splash} ${saindo ? styles.saida : ''}`}
      role="status"
      aria-label="Semente Livre, carregando aplicação"
    >
      <div className={styles.conteudo}>
        <img src="/icons/icon.svg" alt="" width={104} height={104} className={styles.logo} />
        <h1 className={styles.nome}>Semente Livre</h1>
        <p className={styles.slogan}>Banco comunitário de sementes</p>
      </div>
      <span className={styles.ponto} aria-hidden="true" />
      <InstitutionalLogo card />
    </div>
  );
}
