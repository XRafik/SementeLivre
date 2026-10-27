'use client';

import React, { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { apiGet, apiPatch } from '@/lib/api';
import { Notificacao } from '@/types/notification';
import { useAuth } from './AuthContext';

interface NotificationContextType {
  notifications: Notificacao[];
  unreadCount: number;
  markAsRead: (id: string) => Promise<void>;
  markAsUnread: (id: string) => Promise<void>;
  markAllAsRead: () => Promise<void>;
  refresh: () => Promise<void>;
}

const NotificationContext = createContext<NotificationContextType>({
  notifications: [],
  unreadCount: 0,
  markAsRead: async () => {},
  markAsUnread: async () => {},
  markAllAsRead: async () => {},
  refresh: async () => {},
});

// Formato de NotificacaoResponseDTO no backend
interface NotificacaoApi {
  id: string;
  titulo: string;
  mensagem: string;
  lida: boolean;
  dataGeracao: string;
  dataLeitura: string | null;
  proprietarioId: string;
  pedidoRelacionadoId: string | null;
}

function toNotificacao(api: NotificacaoApi): Notificacao {
  return {
    idNotificacao: api.id,
    idProprietario: api.proprietarioId,
    idPedido: api.pedidoRelacionadoId ?? undefined,
    titulo: api.titulo,
    mensagem: api.mensagem,
    lida: api.lida,
    dataGeracao: new Date(api.dataGeracao),
    dataLeitura: api.dataLeitura ? new Date(api.dataLeitura) : undefined,
  };
}

// Notificacoes de pedido sao geradas no backend (CDU-26); sem push, a PWA consulta periodicamente
const POLL_INTERVAL_MS = 30_000;

export function NotificationProvider({ children }: { children: React.ReactNode }) {
  // O login so aceita conta de proprietario; o id da sessao e o do proprietario
  const { user } = useAuth();
  const proprietarioId = user?.uid;
  const [notifications, setNotifications] = useState<Notificacao[]>([]);
  const [loading, setLoading] = useState(false);

  const refresh = useCallback(async () => {
    if (!proprietarioId) return;
    try {
      // O backend ja devolve so as notificacoes de quem esta logado
      const data = await apiGet<NotificacaoApi[]>('/notificacoes');
      setNotifications(data.map(toNotificacao));
    } catch {
      // Mantem a lista anterior; a proxima consulta tenta de novo
    }
  }, [proprietarioId]);

  useEffect(() => {
    if (!proprietarioId) return;
    const first = setTimeout(refresh, 0);
    const interval = setInterval(refresh, POLL_INTERVAL_MS);
    return () => {
      clearTimeout(first);
      clearInterval(interval);
      // Troca de proprietario/logout nao pode exibir notificacoes do anterior
      setNotifications([]);
    };
  }, [proprietarioId, refresh]);

  const markAsRead = useCallback(async (id: string) => {
    const atualizada = toNotificacao(
      await apiPatch<NotificacaoApi>(`/notificacoes/${id}/lida`)
    );
    setNotifications((prev) => prev.map((n) => (n.idNotificacao === id ? atualizada : n)));
  }, []);

  const markAsUnread = useCallback(async (id: string) => {
    const atualizada = toNotificacao(
      await apiPatch<NotificacaoApi>(`/notificacoes/${id}/nao-lida`)
    );
    setNotifications((prev) => prev.map((n) => (n.idNotificacao === id ? atualizada : n)));
  }, []);

  const markAllAsRead = useCallback(async () => {
    const unread = notifications.filter((n) => !n.lida);
    await Promise.all(unread.map((n) => markAsRead(n.idNotificacao)));
  }, [notifications, markAsRead]);

  const unreadCount = notifications.filter((n) => !n.lida).length;

  return (
    <NotificationContext.Provider value={{ notifications, unreadCount, markAsRead, markAsUnread, markAllAsRead, refresh }}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotifications() {
  return useContext(NotificationContext);
}
