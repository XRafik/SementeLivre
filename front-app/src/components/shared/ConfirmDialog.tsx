'use client';

import React, { useId } from 'react';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';

interface ConfirmDialogProps {
  isOpen: boolean;
  title: string;
  description: string;
  confirmLabel?: string;
  confirmVariant?: 'primary' | 'danger';
  onConfirm: () => void;
  onCancel: () => void;
  loading?: boolean;
}

export function ConfirmDialog({
  isOpen,
  title,
  description,
  confirmLabel = 'Confirmar',
  confirmVariant = 'primary',
  onConfirm,
  onCancel,
  loading = false,
}: ConfirmDialogProps) {
  const descricaoId = useId();
  return (
    <Dialog isOpen={isOpen} onClose={onCancel} title={title} describedBy={descricaoId}>
      <p id={descricaoId} style={{ color: 'var(--color-gray-700)', fontSize: 'var(--font-size-base)', marginBottom: 'var(--space-6)', lineHeight: 1.6 }}>
        {description}
      </p>
      <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
        <Button variant="ghost" onClick={onCancel} fullWidth>
          Cancelar
        </Button>
        <Button variant={confirmVariant} onClick={onConfirm} loading={loading} fullWidth>
          {confirmLabel}
        </Button>
      </div>
    </Dialog>
  );
}
