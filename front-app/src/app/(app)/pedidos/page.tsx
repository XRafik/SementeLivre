'use client';

import React, { useState, useMemo } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { ShoppingCart, Search, Plus, ChevronRight } from 'lucide-react';
import { useOrders } from '@/hooks/useOrders';
import { useOrderStock } from '@/hooks/useOrderStock';
import { FiltroPedidos, StatusPedido, StatusPedidoLabels, TipoPedido, TipoPedidoLabels } from '@/types/order';
import { Badge } from '@/components/ui/badge';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { EmptyState } from '@/components/feedback/EmptyState';
import styles from './pedidos.module.css';

const statusFilters = [
  { value: 'TODOS', label: 'Todos' },
  ...Object.entries(StatusPedidoLabels).map(([v, l]) => ({ value: v, label: l })),
];

const tipoOptions = Object.entries(TipoPedidoLabels).map(([value, label]) => ({ value, label }));

export default function PedidosPage() {
  const router = useRouter();
  const [filtroStatus, setFiltroStatus] = useState('TODOS');
  const [filtroTipo, setFiltroTipo] = useState('');
  const [filtroSemente, setFiltroSemente] = useState('');
  const [dataInicio, setDataInicio] = useState('');
  const [dataFim, setDataFim] = useState('');
  const [busca, setBusca] = useState('');

  // Periodo, tipo, semente e status sao filtrados pelo backend (historico);
  // a busca por texto continua local sobre o resultado.
  const filtro: FiltroPedidos = {
    status: filtroStatus === 'TODOS' ? undefined : filtroStatus as StatusPedido,
    tipoPedido: (filtroTipo || undefined) as TipoPedido | undefined,
    produtoId: filtroSemente || undefined,
    dataInicio: dataInicio || undefined,
    dataFim: dataFim || undefined,
  };
  const { orders, loading, error } = useOrders(filtro);
  const { stock } = useOrderStock();

  const sementeOptions = stock.map((s) => ({ value: s.idProduto, label: s.nomePopular }));
  const temFiltro = filtroStatus !== 'TODOS' || !!filtroTipo || !!filtroSemente || !!dataInicio || !!dataFim;
  const periodoInvalido = !!dataInicio && !!dataFim && dataInicio > dataFim;

  const filtered = useMemo(() => {
    const termo = busca.toLowerCase();
    return orders.filter((o) =>
      termo === '' ||
      o.idPedido.toLowerCase().includes(termo) ||
      o.nomeRecebedor.toLowerCase().includes(termo)
    );
  }, [orders, busca]);

  const limparFiltros = () => {
    setFiltroStatus('TODOS');
    setFiltroTipo('');
    setFiltroSemente('');
    setDataInicio('');
    setDataFim('');
  };

  return (
    <div className={styles.page}>
      <div className={styles.topBar}>
        <div className={styles.searchRow}>
          <div className={styles.searchWrap}>
            <Search size={15} strokeWidth={2} className={styles.searchIcon} />
            <input
              type="search"
              className={styles.search}
              placeholder="Buscar pedido por ID ou cliente..."
              value={busca}
              onChange={(e) => setBusca(e.target.value)}
              aria-label="Buscar pedido"
            />
          </div>
          <Link href="/pedidos/novo" className={styles.addBtn} aria-label="Novo pedido">
            <Plus size={18} strokeWidth={2.5} />
          </Link>
        </div>
        <div className={styles.filters} role="group" aria-label="Filtrar por status">
          {statusFilters.map((f) => (
            <button key={f.value} className={`${styles.chip} ${filtroStatus === f.value ? styles.chipActive : ''}`} onClick={() => setFiltroStatus(f.value)} aria-pressed={filtroStatus === f.value}>
              {f.label}
            </button>
          ))}
        </div>
        <div className={styles.historyFilters}>
          <Select label="Tipo" value={filtroTipo} onChange={(e) => setFiltroTipo(e.target.value)} options={[{ value: '', label: 'Todos' }, ...tipoOptions]} />
          <Select label="Semente" value={filtroSemente} onChange={(e) => setFiltroSemente(e.target.value)} options={[{ value: '', label: 'Todas' }, ...sementeOptions]} />
          <Input label="De" type="date" value={dataInicio} max={dataFim || undefined} onChange={(e) => setDataInicio(e.target.value)} />
          <Input label="Até" type="date" value={dataFim} min={dataInicio || undefined} onChange={(e) => setDataFim(e.target.value)} error={periodoInvalido ? 'Data final antes da inicial' : undefined} />
        </div>
        {temFiltro && (
          <button type="button" className={styles.clearFilters} onClick={limparFiltros}>Limpar filtros</button>
        )}
      </div>

      {error ? (
        <EmptyState icon={<ShoppingCart size={38} strokeWidth={1.5} />} title="Não foi possível carregar os pedidos" description={error} />
      ) : loading ? (
        <div className={styles.list}>
          {[1, 2, 3].map((i) => <div key={i} className={`skeleton ${styles.skeletonCard}`} />)}
        </div>
      ) : filtered.length === 0 ? (
        orders.length === 0 && !temFiltro ? (
          <EmptyState
            icon={<ShoppingCart size={38} strokeWidth={1.5} />}
            title="Nenhum pedido registrado"
            description="Registre um pedido de venda, troca ou doacao."
            actionLabel="Registrar Pedido"
            onAction={() => router.push('/pedidos/novo')}
          />
        ) : (
          <EmptyState icon={<Search size={34} strokeWidth={1.5} />} title="Nenhum pedido encontrado com esses filtros." />
        )
      ) : (
        <ul className={styles.list}>
          {filtered.map((order) => (
            <li key={order.idPedido}>
              <Link href={`/pedidos/${order.idPedido}`} className={styles.card}>
                <div className={styles.cardIconBox}>
                  <ShoppingCart size={16} strokeWidth={2} />
                </div>
                <div className={styles.cardBody}>
                  <div className={styles.cardHeader}>
                    <span className={styles.cardId}>#{order.idPedido.slice(-6).toUpperCase()}</span>
                    <Badge variant="orderStatus" value={order.status} />
                  </div>
                  <p className={styles.cardReceber}>{order.nomeRecebedor}</p>
                  <div className={styles.cardFooter}>
                    <Badge variant="orderType" value={order.tipoPedido} />
                    <span className={styles.cardDate}>{order.dataPedido.toLocaleDateString('pt-BR')}</span>
                  </div>
                </div>
                <ChevronRight size={16} strokeWidth={2} className={styles.chevron} />
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
