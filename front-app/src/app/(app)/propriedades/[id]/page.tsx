'use client';

import React, { useMemo } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { ArrowLeft, MapPin, TreePine, Users, Ruler, Hash, MapPinned } from 'lucide-react';
import { useProperties } from '@/hooks/useProperties';
import { useToast } from '@/components/feedback/Toast';
import { EmptyState } from '@/components/feedback/EmptyState';
import styles from './propriedade.module.css';

function formatarData(d: Date): string {
  return d.toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export default function PropriedadeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { properties, loading } = useProperties();
  const { showToast } = useToast();

  const prop = useMemo(
    () => properties.find((p) => p.idPropriedade === id) ?? null,
    [properties, id]
  );

  const copiarEndereco = () => {
    if (!prop) return;
    const texto = `${prop.logradouro}, ${prop.numero}${prop.complemento ? ` - ${prop.complemento}` : ''} - ${prop.bairro}, ${prop.municipio}/${prop.uf}`;
    navigator.clipboard?.writeText(texto);
    showToast('Endereço copiado!', 'success');
  };

  if (loading) return <div className={styles.loading}><div className="skeleton" style={{ height: 200, borderRadius: 16 }} /></div>;

  if (!prop) {
    return (
      <EmptyState
        icon={<TreePine size={38} strokeWidth={1.5} />}
        title="Propriedade não encontrada"
        description="Ela pode ter sido removida."
        actionLabel="Voltar"
        onAction={() => router.push('/propriedades')}
      />
    );
  }

  return (
    <div className={styles.page}>
      <button type="button" className={styles.back} onClick={() => router.push('/propriedades')}>
        <ArrowLeft size={16} strokeWidth={2.5} />
        <span>Propriedades</span>
      </button>

      <header className={styles.header}>
        <div className={styles.headerIcon} aria-hidden="true">
          <TreePine size={24} strokeWidth={1.75} />
        </div>
        <div className={styles.headerContent}>
          <h1 className={styles.headerTitle}>{prop.nome}</h1>
          <p className={styles.headerSubtitle}>
            {prop.municipio}/{prop.uf}
          </p>
        </div>
      </header>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Dados da propriedade</h2>
        <ul className={styles.detailList}>
          {prop.tamanhoHectares != null && (
            <li className={styles.detailRow}>
              <Ruler size={16} strokeWidth={2} className={styles.detailIcon} />
              <span className={styles.detailLabel}>Área</span>
              <span className={styles.detailValue}>{prop.tamanhoHectares} hectares</span>
            </li>
          )}
          <li className={styles.detailRow}>
            <Users size={16} strokeWidth={2} className={styles.detailIcon} />
            <span className={styles.detailLabel}>Comunidade</span>
            <span className={styles.detailValue}>{prop.nomeComunidade}</span>
          </li>
          <li className={styles.detailRow}>
            <MapPin size={16} strokeWidth={2} className={styles.detailIcon} />
            <span className={styles.detailLabel}>Localização</span>
            <span className={styles.detailValue}>
              {prop.municipio}/{prop.uf}
            </span>
          </li>
          <li className={styles.detailRow} onClick={copiarEndereco} title="Copiar endereço">
            <MapPinned size={16} strokeWidth={2} className={styles.detailIcon} />
            <span className={styles.detailLabel}>Logradouro</span>
            <span className={styles.detailValue}>
              {prop.logradouro}, {prop.numero}
              {prop.complemento ? ` - ${prop.complemento}` : ''}
            </span>
          </li>
          <li className={styles.detailRow}>
            <Hash size={16} strokeWidth={2} className={styles.detailIcon} />
            <span className={styles.detailLabel}>CEP</span>
            <span className={styles.detailValue}>{prop.cep}</span>
          </li>
        </ul>
        <p className={styles.hint}>Clique no endereço para copiá-lo.</p>
      </section>

      <section className={styles.section}>
        <h2 className={styles.sectionTitle}>Histórico</h2>
        <ul className={styles.detailList}>
          <li className={styles.detailRow}>
            <span className={styles.detailLabel}>Cadastrada em</span>
            <span className={styles.detailValue}>{formatarData(prop.dataCadastro)}</span>
          </li>
          <li className={styles.detailRow}>
            <span className={styles.detailLabel}>Última alteração</span>
            <span className={styles.detailValue}>{formatarData(prop.dataUltimaAlteracao)}</span>
          </li>
        </ul>
      </section>
    </div>
  );
}
