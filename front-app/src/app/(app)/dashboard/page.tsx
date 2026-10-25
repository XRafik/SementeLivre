'use client';

/**
 * dashboard/page.tsx
 *
 * ALTERAÇÕES em relação à versão anterior:
 *   - `proprietario` removido do destructuring de `useAuth()` — o contexto não expõe mais
 *     esse campo após a integração com backend
 *   - `user.nome` (AuthSession) agora fornece o primeiro nome, em substituição a `proprietario?.nome`
 *   - `firstName` usa `user?.nome?.split(' ')[0]` ao invés de `proprietario?.nome?.split(' ')[0]`
 *
 * LINHAS REMOVIDAS (comparado ao arquivo anterior):
 *   - const { proprietario } = useAuth();    → substituído por const { user } = useAuth();
 *   - const firstName = proprietario?.nome?.split(' ')[0] || 'Produtor';
 *     → substituído por const firstName = user?.nome?.split(' ')[0] || user?.email?.split('@')[0] || 'Produtor';
 */

import React from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Sprout, ShoppingCart, MapPin, Bell, AlertTriangle, Plus, ChevronRight, TrendingUp, FileText } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { useSeeds } from '@/hooks/useSeeds';
import { useOrders } from '@/hooks/useOrders';
import { useProperties } from '@/hooks/useProperties';
import { useNotifications } from '@/context/NotificationContext';
import { DisponibilidadeProduto } from '@/types/stock';
import { Badge } from '@/components/ui/badge';
import styles from './dashboard.module.css';

interface StatCardProps {
  icon: React.ReactNode;
  label: string;
  value: number | string;
  accent: string;
  accentBg: string;
}

function StatCard({ icon, label, value, accent, accentBg }: StatCardProps) {
  return (
    <div className={styles.statCard} aria-label={`${label}: ${value}`}>
      <div className={styles.statIconWrap} style={{ background: accentBg, color: accent }}>
        {icon}
      </div>
      <span className={styles.statValue} style={{ color: accent }}>{value}</span>
      <span className={styles.statLabel}>{label}</span>
    </div>
  );
}

export default function DashboardPage() {
  const { user } = useAuth();
  const { seeds, loading: loadingSeeds } = useSeeds();
  const { orders, loading: loadingOrders } = useOrders();
  const { properties } = useProperties();
  const { unreadCount } = useNotifications();
  const router = useRouter();

  const firstName = user?.nome?.split(' ')[0] || user?.email?.split('@')[0] || 'Produtor';

  const now = new Date();
  const ordersThisMonth = orders.filter((o) => {
    const d = o.dataPedido;
    return d.getMonth() === now.getMonth() && d.getFullYear() === now.getFullYear();
  }).length;

  const lowStock = seeds.filter((s) =>
    s.disponibilidade !== DisponibilidadeProduto.INDISPONIVEL && s.quantidade <= 5
  );

  const greeting = () => {
    const h = now.getHours();
    if (h < 12) return 'Bom dia';
    if (h < 18) return 'Boa tarde';
    return 'Boa noite';
  };

  return (
    <div className={styles.page}>
      {/* Greeting */}
      <div className={styles.greeting}>
        <div className={styles.greetingContent}>
          <p className={styles.greetingHello}>{greeting()},</p>
          <p className={styles.greetingName}>{firstName}</p>
          <p className={styles.greetingDate}>
            {now.toLocaleDateString('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' })}
          </p>
        </div>
        <div className={styles.greetingOrb} aria-hidden="true">
          <Sprout size={40} strokeWidth={1.5} />
        </div>
      </div>

      {/* Stats */}
      <section aria-label="Resumo" className={styles.statsGrid}>
        {loadingSeeds || loadingOrders ? (
          <>
            {[1, 2, 3, 4].map((i) => (
              <div key={i} className={`${styles.statCard} skeleton`} style={{ height: 96 }} />
            ))}
          </>
        ) : (
          <>
            <StatCard
              icon={<Sprout size={18} strokeWidth={2} />}
              label="Produtos"
              value={seeds.length}
              accent="var(--color-primary)"
              accentBg="var(--color-primary-light)"
            />
            <StatCard
              icon={<ShoppingCart size={18} strokeWidth={2} />}
              label="Pedidos/mês"
              value={ordersThisMonth}
              accent="var(--color-info)"
              accentBg="var(--color-info-light)"
            />
            <StatCard
              icon={<MapPin size={18} strokeWidth={2} />}
              label="Propriedades"
              value={properties.length}
              accent="var(--color-purple)"
              accentBg="var(--color-purple-light)"
            />
            <StatCard
              icon={<Bell size={18} strokeWidth={2} />}
              label="Notificações"
              value={unreadCount}
              accent="var(--color-warning)"
              accentBg="var(--color-warning-light)"
            />
          </>
        )}
      </section>

      {/* Low stock alert */}
      {lowStock.length > 0 && (
        <section className={styles.alertSection} aria-label="Produtos com baixo estoque">
          <div className={styles.sectionHeader}>
            <div className={styles.sectionTitleWrap}>
              <AlertTriangle size={15} strokeWidth={2.5} className={styles.alertIcon} />
              <h2 className={styles.sectionTitle}>Estoque baixo</h2>
            </div>
            <Link href="/sementes" className={styles.sectionLink}>
              Ver todos <ChevronRight size={13} strokeWidth={2.5} />
            </Link>
          </div>
          <div className={styles.alertList}>
            {lowStock.slice(0, 3).map((s) => (
              <Link key={s.idEstoque} href={`/sementes/${s.idEstoque}`} className={styles.alertCard}>
                <div className={styles.alertIconBox}>
                  <Sprout size={16} strokeWidth={2} />
                </div>
                <div className={styles.alertInfo}>
                  <p className={styles.alertName}>{s.nomePopular}</p>
                  <p className={styles.alertQty}>{s.quantidade} {s.tipoPesagem}</p>
                </div>
                <Badge variant="availability" value={s.disponibilidade} />
                <ChevronRight size={14} className={styles.alertChevron} />
              </Link>
            ))}
          </div>
        </section>
      )}

      {/* Quick actions */}
      <section aria-label="Ações rápidas" className={styles.shortcuts}>
        <div className={styles.sectionHeader}>
          <div className={styles.sectionTitleWrap}>
            <TrendingUp size={15} strokeWidth={2.5} className={styles.actionIcon} />
            <h2 className={styles.sectionTitle}>Ações rápidas</h2>
          </div>
        </div>
        <div className={styles.shortcutGrid}>
          <button className={styles.shortcutBtn} onClick={() => router.push('/sementes/nova')}>
            <div className={styles.shortcutIconWrap}>
              <Plus size={18} strokeWidth={2.5} />
            </div>
            <div className={styles.shortcutText}>
              <span className={styles.shortcutLabel}>Novo Produto</span>
              <span className={styles.shortcutSub}>Cadastrar no estoque</span>
            </div>
            <ChevronRight size={16} className={styles.shortcutChevron} />
          </button>
          <button className={styles.shortcutBtn} onClick={() => router.push('/pedidos/novo')}>
            <div className={styles.shortcutIconWrap} style={{ background: 'var(--color-info-light)', color: 'var(--color-info)' }}>
              <ShoppingCart size={18} strokeWidth={2.5} />
            </div>
            <div className={styles.shortcutText}>
              <span className={styles.shortcutLabel}>Novo Pedido</span>
              <span className={styles.shortcutSub}>Registrar entrada/saída</span>
            </div>
            <ChevronRight size={16} className={styles.shortcutChevron} />
          </button>
          <button className={styles.shortcutBtn} onClick={() => router.push('/relatorios')}>
            <div className={styles.shortcutIconWrap} style={{ background: 'var(--color-purple-light)', color: 'var(--color-purple)' }}>
              <FileText size={18} strokeWidth={2.5} />
            </div>
            <div className={styles.shortcutText}>
              <span className={styles.shortcutLabel}>Relatórios</span>
              <span className={styles.shortcutSub}>Estoque e pedidos em PDF/CSV</span>
            </div>
            <ChevronRight size={16} className={styles.shortcutChevron} />
          </button>
        </div>
      </section>
    </div>
  );
}
