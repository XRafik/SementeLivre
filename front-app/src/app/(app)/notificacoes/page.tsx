'use client';

import React from 'react';
import { Bell, ShoppingCart } from 'lucide-react';
import { useNotifications } from '@/context/NotificationContext';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/feedback/EmptyState';
import styles from './notificacoes.module.css';

function timeAgo(date: Date): string {
  const diff = Date.now() - date.getTime();
  const m = Math.floor(diff / 60000);
  const h = Math.floor(m / 60);
  const d = Math.floor(h / 24);
  if (d > 0) return `ha ${d} dia${d > 1 ? 's' : ''}`;
  if (h > 0) return `ha ${h} hora${h > 1 ? 's' : ''}`;
  if (m > 0) return `ha ${m} minuto${m > 1 ? 's' : ''}`;
  return 'agora';
}

export default function NotificacoesPage() {
  const { notifications, markAsRead, markAsUnread, markAllAsRead } = useNotifications();

  // Tocar so marca como lida: a notificacao e o aviso em si, nao um atalho para o pedido
  const handleNotif = async (idNotificacao: string, lida: boolean) => {
    if (!lida) await markAsRead(idNotificacao).catch(() => {});
  };

  return (
    <div className={styles.page}>
      {notifications.length > 0 && (
        <div className={styles.topBar}>
          <Button variant="text" size="sm" onClick={markAllAsRead}>Marcar todas como lidas</Button>
        </div>
      )}

      {notifications.length === 0 ? (
        <EmptyState
          icon={<Bell size={38} strokeWidth={1.5} />}
          title="Nenhuma notificacao"
          description="Voce sera notificado quando registrar um pedido."
        />
      ) : (
        <ul className={styles.list}>
          {notifications.map((n) => (
            <li key={n.idNotificacao}>
              <button
                className={`${styles.item} ${!n.lida ? styles.unread : ''}`}
                onClick={() => handleNotif(n.idNotificacao, n.lida)}
                aria-label={`${n.titulo}${!n.lida ? ' - nao lida' : ''}`}
              >
                {!n.lida && <span className={styles.dot} aria-hidden="true" />}
                <div className={styles.icon} aria-hidden="true">
                  <ShoppingCart size={16} strokeWidth={2} />
                </div>
                <div className={styles.content}>
                  <p className={styles.title}>{n.titulo}</p>
                  <p className={styles.message}>{n.mensagem}</p>
                  <p className={styles.time}>{timeAgo(n.dataGeracao)}</p>
                </div>
              </button>
              {n.lida && (
                <Button variant="text" size="sm" onClick={() => markAsUnread(n.idNotificacao).catch(() => {})}>
                  Marcar como não lida
                </Button>
              )}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
