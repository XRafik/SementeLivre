'use client';

/**
 * AuthContext.tsx — Contexto de autenticação integrado ao backend REST.
 *
 *   - A sessão vem do cookie HttpOnly de /api/sessao (lib/api.ts): na montagem, sem
 *     access token na aba, renova pela sessão e chama GET /auth/me para carregar
 *     o perfil completo do proprietário.
 *   - `user` é o resumo da sessão (uid + email + nome); `perfil` é a resposta completa do /auth/me.
 *   - Escuta `sl:session-expired` (disparado por api.ts quando o refresh falha) para sair.
 *   - Sem conexão na revalidação: mantém os tokens e expõe `semConexao`, para o layout
 *     oferecer "tentar novamente" em vez de deslogar.
 *   - Logout automático após 30 min de inatividade.
 */

import React, { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { AuthSession, logoutApi, meApi, sessaoDoPerfil } from '@/lib/authApi';
import { isApiError, verificarSessao } from '@/lib/api';
import { PerfilUsuario } from '@/types/user';

interface AuthContextType {
  user: AuthSession | null;
  perfil: PerfilUsuario | null;
  loading: boolean;
  semConexao: boolean;
  /** Recarrega o perfil de GET /auth/me. Lança o erro da API se falhar. */
  recarregarPerfil: () => Promise<void>;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType>({
  user: null,
  perfil: null,
  loading: true,
  semConexao: false,
  recarregarPerfil: async () => {},
  logout: async () => {},
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [perfil, setPerfil] = useState<PerfilUsuario | null>(null);
  const [loading, setLoading] = useState(true);
  const [semConexao, setSemConexao] = useState(false);
  const inactivityTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const doLogout = () => {
    logoutApi();
    setPerfil(null);
  };

  const resetTimer = () => {
    if (inactivityTimer.current) clearTimeout(inactivityTimer.current);
    inactivityTimer.current = setTimeout(() => {
      doLogout();
    }, 30 * 60 * 1000); // 30 min de inatividade
  };

  const recarregarPerfil = useCallback(async () => {
    const me = await meApi();
    setPerfil(me);
    setSemConexao(false);
  }, []);

  // ── Revalidar sessão na montagem ──────────────────────────────────────────
  useEffect(() => {
    let cancelled = false;

    const init = async () => {
      const sessao = await verificarSessao();
      if (cancelled) return;
      if (sessao !== 'ok') {
        // Servidor fora do ar: a sessão continua no cookie, o layout oferece tentar de novo
        if (sessao === 'indisponivel') setSemConexao(true);
        setLoading(false);
        return;
      }
      try {
        await recarregarPerfil();
        if (!cancelled) resetTimer();
      } catch (err) {
        if (cancelled) return;
        if (isApiError(err) && err.status === 0) {
          setSemConexao(true); // mantém os tokens; o usuário pode tentar de novo
        } else {
          // Token inválido ou expirado sem refresh → limpa estado
          doLogout();
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    };

    init();

    // Escuta evento de sessão expirada disparado por api.ts
    const handleExpired = () => {
      setPerfil(null);
    };
    window.addEventListener('sl:session-expired', handleExpired);

    const events = ['mousedown', 'keydown', 'touchstart', 'scroll'];
    events.forEach((e) => document.addEventListener(e, resetTimer));

    return () => {
      cancelled = true;
      if (inactivityTimer.current) clearTimeout(inactivityTimer.current);
      window.removeEventListener('sl:session-expired', handleExpired);
      events.forEach((e) => document.removeEventListener(e, resetTimer));
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const logout = async () => {
    doLogout();
  };

  // Memoizado: hooks que dependem de `user` não re-executam a cada render.
  const user = useMemo(() => (perfil ? sessaoDoPerfil(perfil) : null), [perfil]);

  return (
    <AuthContext.Provider value={{ user, perfil, loading, semConexao, recarregarPerfil, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  return useContext(AuthContext);
}
