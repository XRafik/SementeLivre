'use client';

import React, { useEffect } from 'react';
import { AuthProvider, useAuth } from '@/context/AuthContext';
import { NotificationProvider } from '@/context/NotificationContext';
import { Header } from '@/components/layout/Header';
import { BottomNavigation } from '@/components/layout/BottomNavigation';
import { Button } from '@/components/ui/button';
import { usePathname, useRouter } from 'next/navigation';
import styles from './app.module.css';

const headerConfig: Record<string, { title: string; showBack?: boolean; showNotifications?: boolean }> = {
  '/dashboard': { title: 'Semente Livre', showNotifications: true },
  '/sementes': { title: 'Meus Produtos', showNotifications: true },
  '/sementes/nova': { title: 'Cadastrar Produto', showBack: true },
  '/pedidos': { title: 'Pedidos', showNotifications: true },
  '/pedidos/novo': { title: 'Novo Pedido', showBack: true },
  '/propriedades': { title: 'Propriedades', showNotifications: true },
  '/propriedades/nova': { title: 'Nova Propriedade', showBack: true },
  '/relatorios': { title: 'Relatórios', showBack: true },
  '/notificacoes': { title: 'Notificações', showBack: true },
  '/perfil': { title: 'Meu Perfil', showNotifications: true },
  '/privacidade': { title: 'Política de Privacidade', showBack: true },
  '/termos': { title: 'Termos de Uso', showBack: true },
};

function AppLayoutInner({ children }: { children: React.ReactNode }) {
  const { user, loading, semConexao } = useAuth();
  const router = useRouter();
  const pathname = usePathname();

  useEffect(() => {
    if (!loading && !user && !semConexao) {
      router.replace('/entrar');
    }
  }, [loading, user, semConexao, router]);

  if (loading) {
    return (
      <div style={{ display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center' }}>
        <p style={{ color: 'var(--color-text-muted)', fontFamily: 'sans-serif' }}>Carregando sessão...</p>
      </div>
    );
  }

  // Sessão salva, mas o backend não respondeu: não desloga, oferece tentar de novo.
  if (!user && semConexao) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', minHeight: '100vh', alignItems: 'center', justifyContent: 'center', padding: 'var(--content-padding)' }}>
        <p style={{ color: 'var(--color-text-muted)', textAlign: 'center' }}>Não foi possível falar com o servidor.</p>
        <Button variant="primary" onClick={() => window.location.reload()}>Tentar novamente</Button>
      </div>
    );
  }

  if (!user) {
    return null;
  }

  const config = Object.entries(headerConfig).find(([key]) =>
    pathname === key || (key !== '/sementes' && key !== '/pedidos' && key !== '/propriedades' && pathname.startsWith(key + '/'))
  )?.[1] || { title: 'Semente Livre', showBack: true };

  return (
    <div className={styles.appShell}>
      <Header
        title={config.title}
        showBack={config.showBack}
        showNotifications={config.showNotifications}
      />
      <main className={styles.content}>
        {children}
      </main>
      <BottomNavigation />
    </div>
  );
}

export default function AppLayout({ children }: { children: React.ReactNode }) {
  return (
    <AuthProvider>
      <NotificationProvider>
        <AppLayoutInner>{children}</AppLayoutInner>
      </NotificationProvider>
    </AuthProvider>
  );
}
