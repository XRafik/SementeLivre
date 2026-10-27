'use client';

import React, { useEffect, useId, useRef } from 'react';
import clsx from 'clsx';
import styles from './dialog.module.css';

interface DialogProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  children: React.ReactNode;
  className?: string;
  /** id do texto que descreve o diálogo (aria-describedby). */
  describedBy?: string;
}

const FOCAVEIS = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';

/**
 * Diálogo modal no padrão WAI-ARIA (#182): foco entra ao abrir, fica preso
 * no diálogo enquanto aberto (Tab e Shift+Tab giram dentro dele), Esc fecha
 * e o foco volta para quem abriu.
 */
export function Dialog({ isOpen, onClose, title, children, className, describedBy }: DialogProps) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  // Quem usa passa onClose inline; guardado em ref, o efeito roda só ao abrir.
  // Antes cada renderização do pai devolvia o foco ao primeiro botão, e quem
  // digitava num campo do diálogo perdia o foco a cada tecla.
  const onCloseRef = useRef(onClose);
  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!isOpen) return;
    const anterior = document.activeElement as HTMLElement | null;
    const focaveis = () => Array.from(dialogRef.current?.querySelectorAll<HTMLElement>(FOCAVEIS) ?? []);

    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCloseRef.current();
        return;
      }
      if (e.key !== 'Tab') return;
      const lista = focaveis();
      if (lista.length === 0) return;
      const primeiro = lista[0];
      const ultimo = lista[lista.length - 1];
      if (e.shiftKey && document.activeElement === primeiro) {
        e.preventDefault();
        ultimo.focus();
      } else if (!e.shiftKey && document.activeElement === ultimo) {
        e.preventDefault();
        primeiro.focus();
      }
    };

    document.addEventListener('keydown', handleKey);
    document.body.style.overflow = 'hidden';
    const foco = setTimeout(() => focaveis()[0]?.focus(), 50);

    return () => {
      clearTimeout(foco);
      document.removeEventListener('keydown', handleKey);
      document.body.style.overflow = '';
      anterior?.focus?.();
    };
  }, [isOpen]);

  if (!isOpen) return null;

  return (
    <div className={styles.overlay} onClick={onClose}>
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={describedBy}
        className={clsx(styles.panel, className)}
        onClick={(e) => e.stopPropagation()}
      >
        <div className={styles.header}>
          <h2 id={titleId} className={styles.title}>{title}</h2>
          <button
            type="button"
            onClick={onClose}
            className={styles.closeBtn}
            aria-label="Fechar"
          >
            <span aria-hidden="true">✕</span>
          </button>
        </div>
        <div className={styles.body}>{children}</div>
      </div>
    </div>
  );
}
