'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { verificarSessao } from '@/lib/api';

export default function Home() {
  const router = useRouter();

  useEffect(() => {
    let cancelado = false;
    // Servidor fora do ar com sessão salva também vai ao dashboard, que oferece tentar de novo
    void verificarSessao().then((sessao) => {
      if (!cancelado) router.replace(sessao === 'sem-sessao' ? '/entrar' : '/dashboard');
    });
    return () => {
      cancelado = true;
    };
  }, [router]);

  return (
    <div style={{ display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center' }}>
      <p style={{ color: 'var(--color-text-muted)', fontFamily: 'sans-serif' }}>Carregando...</p>
    </div>
  );
}
