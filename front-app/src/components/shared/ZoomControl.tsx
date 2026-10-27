'use client';

import React from 'react';
import { Minus, Plus, RotateCcw, Type } from 'lucide-react';
import { useTextScale } from '@/hooks/useTextScale';
import styles from './ZoomControl.module.css';

export function ZoomControl() {
  const { percent, isDefault, canIncrease, canDecrease, increase, decrease, reset } = useTextScale();

  return (
    <div className={styles.container}>
      <div className={styles.labelWrap}>
        <div className={styles.icon} aria-hidden="true">
          <Type size={15} strokeWidth={2} />
        </div>
        <div className={styles.labelText}>
          <span className={styles.label}>Tamanho do texto</span>
          <span className={styles.hint}>{percent}%</span>
        </div>
      </div>

      <div className={styles.controls}>
        <button
          type="button"
          onClick={decrease}
          disabled={!canDecrease}
          className={styles.button}
          aria-label="Diminuir tamanho do texto"
        >
          <Minus size={18} strokeWidth={2.5} />
        </button>

        <span className={styles.value} aria-live="polite">
          {percent}%
        </span>

        <button
          type="button"
          onClick={increase}
          disabled={!canIncrease}
          className={styles.button}
          aria-label="Aumentar tamanho do texto"
        >
          <Plus size={18} strokeWidth={2.5} />
        </button>

        <button
          type="button"
          onClick={reset}
          disabled={isDefault}
          className={`${styles.button} ${styles.resetButton}`}
          aria-label="Restaurar tamanho padrão do texto"
          title="Restaurar padrão"
        >
          <RotateCcw size={15} strokeWidth={2.5} />
        </button>
      </div>
    </div>
  );
}
