'use client';

import React, { useMemo, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Search, Plus, Sprout, ChevronRight, PackageSearch, SlidersHorizontal } from 'lucide-react';
import { useSeeds } from '@/hooks/useSeeds';
import { DisponibilidadeProduto, DisponibilidadeLabels } from '@/types/stock';
import { TipoProdutoLabels } from '@/types/seed';
import { Badge } from '@/components/ui/badge';
import { EmptyState } from '@/components/feedback/EmptyState';
import styles from './sementes.module.css';

const disponibilidadeFilters = [
  { value: 'TODAS', label: 'Todos' },
  ...Object.entries(DisponibilidadeLabels).map(([v, l]) => ({ value: v, label: l })),
];

export default function SementesPage() {
  const { seeds, loading } = useSeeds();
  const router = useRouter();
  const [busca, setBusca] = useState('');
  const [filtroDisp, setFiltroDisp] = useState('TODAS');

  const filtered = useMemo(() => {
    return seeds.filter((s) => {
      const matchBusca =
        busca === '' || s.nomePopular.toLowerCase().includes(busca.toLowerCase());
      const matchDisp = filtroDisp === 'TODAS' || s.disponibilidade === filtroDisp;
      return matchBusca && matchDisp;
    });
  }, [seeds, busca, filtroDisp]);

  return (
    <div className={styles.page}>
      {/* Search bar */}
      <div className={styles.topBar}>
        <div className={styles.searchWrap}>
          <Search size={15} strokeWidth={2} className={styles.searchIcon} />
          <input
            type="search"
            className={styles.search}
            placeholder="Buscar produto..."
            value={busca}
            onChange={(e) => setBusca(e.target.value)}
            aria-label="Buscar produto"
          />
        </div>
        <Link href="/produtos/nova" className={styles.addBtn} aria-label="Cadastrar novo produto">
          <Plus size={18} strokeWidth={2.5} />
        </Link>
      </div>

      {/* Filters */}
      <div className={styles.filters} role="group" aria-label="Filtrar por disponibilidade">
        {disponibilidadeFilters.map((f) => (
          <button
            key={f.value}
            className={`${styles.chip} ${filtroDisp === f.value ? styles.chipActive : ''}`}
            onClick={() => setFiltroDisp(f.value)}
            aria-pressed={filtroDisp === f.value}
          >
            {f.label}
          </button>
        ))}
      </div>

      {/* List */}
      {loading ? (
        <div className={styles.list}>
          {[1, 2, 3].map((i) => (
            <div key={i} className={`skeleton ${styles.skeletonCard}`} />
          ))}
        </div>
      ) : filtered.length === 0 ? (
        seeds.length === 0 ? (
          <EmptyState
            icon={<PackageSearch size={40} strokeWidth={1.5} />}
            title="Nenhum produto cadastrado"
            description="Cadastre seu primeiro produto para começar a gerenciar seu estoque."
            actionLabel="Cadastrar Produto"
            onAction={() => router.push('/produtos/nova')}
          />
        ) : (
          <EmptyState
            icon={<Search size={36} strokeWidth={1.5} />}
            title="Nenhum resultado"
            description="Tente outro filtro ou termo de busca."
          />
        )
      ) : (
        <ul className={styles.list} aria-label={`${filtered.length} produtos encontrados`}>
          {filtered.map((seed) => (
            <li key={seed.idEstoque}>
              <Link href={`/produtos/${seed.idEstoque}`} className={styles.card}>
                <div className={styles.cardImg}>
                  {seed.urlFoto ? (
                    <img src={seed.urlFoto} alt={`Foto de ${seed.nomePopular}`} className={styles.img} />
                  ) : (
                    <div className={styles.imgPlaceholder} aria-hidden="true">
                      <Sprout size={22} strokeWidth={1.5} />
                    </div>
                  )}
                </div>
                <div className={styles.cardContent}>
                  <p className={styles.cardName}>{seed.nomePopular}</p>
                  <p className={styles.cardQty}>{seed.quantidade} {seed.tipoPesagem}</p>
                  <Badge variant="availability" value={seed.disponibilidade} />
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
