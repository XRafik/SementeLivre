'use client';

import { useCallback, useEffect, useSyncExternalStore } from 'react';

const STORAGE_KEY = 'sementelivre:textScale';
const BASE_FONT = 16;
const MIN = 0.8;
const MAX = 1.5;
const STEP = 0.1;
const EVENT = 'textscale-change';

function isValid(v: unknown): v is number {
  return typeof v === 'number' && Number.isFinite(v) && v >= MIN && v <= MAX;
}

function read(): number {
  if (typeof window === 'undefined') return 1;
  const raw = window.localStorage.getItem(STORAGE_KEY);
  if (!raw) return 1;
  const parsed = Number(raw);
  return isValid(parsed) ? parsed : 1;
}

function write(value: number): void {
  const clamped = Math.min(MAX, Math.max(MIN, Number(value.toFixed(2))));
  window.localStorage.setItem(STORAGE_KEY, String(clamped));
  window.dispatchEvent(new Event(EVENT));
}

function subscribe(callback: () => void): () => void {
  window.addEventListener(EVENT, callback);
  window.addEventListener('storage', callback);
  return () => {
    window.removeEventListener(EVENT, callback);
    window.removeEventListener('storage', callback);
  };
}

/**
 * Escala o tamanho base da fonte, o que redimensiona tudo que usa rem.
 * O valor fica no localStorage para a preferência sobreviver a recargas.
 */
export function useTextScale() {
  const scale = useSyncExternalStore(subscribe, read, () => 1);

  useEffect(() => {
    document.documentElement.style.fontSize = `${BASE_FONT * scale}px`;
  }, [scale]);

  const setScale = useCallback((value: number) => write(value), []);

  return {
    scale,
    percent: Math.round(scale * 100),
    isDefault: Math.abs(scale - 1) < 0.001,
    canIncrease: scale < MAX - 0.001,
    canDecrease: scale > MIN + 0.001,
    increase: useCallback(() => write(read() + STEP), []),
    decrease: useCallback(() => write(read() - STEP), []),
    reset: useCallback(() => write(1), []),
    setScale,
  };
}
