'use client';

import React, { useId } from 'react';
import clsx from 'clsx';
import styles from './input.module.css';

interface InputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
  error?: string;
  hint?: string;
  leftIcon?: React.ReactNode;
  rightIcon?: React.ReactNode;
  onRightIconClick?: () => void;
  /** Nome do botão do ícone à direita para leitor de tela (ex.: "Mostrar senha"). */
  rightIconLabel?: string;
}

export const Input = React.forwardRef<HTMLInputElement, InputProps>(
  ({ label, error, hint, leftIcon, rightIcon, onRightIconClick, rightIconLabel = 'Ação do campo', required, className, id, ...props }, ref) => {
    const generatedId = useId();
    const inputId = id || generatedId;
    const errorId = `${inputId}-error`;
    const hintId = `${inputId}-hint`;

    return (
      <div className={styles.wrapper}>
        {label && (
          <label htmlFor={inputId} className={styles.label}>
            {label}
            {required && <span className={styles.required} aria-hidden="true">*</span>}
          </label>
        )}
        <div className={styles.inputWrapper}>
          {leftIcon && <span className={styles.leftIcon} aria-hidden="true">{leftIcon}</span>}
          <input
            ref={ref}
            id={inputId}
            className={clsx(
              styles.input,
              leftIcon && styles.hasLeft,
              rightIcon && styles.hasRight,
              error && styles.error,
              className
            )}
            aria-invalid={error ? 'true' : undefined}
            aria-describedby={clsx(error && errorId, hint && hintId) || undefined}
            required={required}
            {...props}
          />
          {rightIcon && (
            <button
              type="button"
              className={styles.rightIcon}
              onClick={onRightIconClick}
              aria-label={rightIconLabel}
            >
              <span aria-hidden="true">{rightIcon}</span>
            </button>
          )}
        </div>
        {hint && !error && <span id={hintId} className={styles.hint}>{hint}</span>}
        {error && (
          <span id={errorId} className={styles.errorMessage} role="alert">
            ⚠ {error}
          </span>
        )}
      </div>
    );
  }
);

Input.displayName = 'Input';
