'use client';

import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { ArrowLeft, ArrowDown, ArrowUp, History, Plus, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { EmptyState } from '@/components/feedback/EmptyState';
import { useToast } from '@/components/feedback/Toast';
import { useSeeds } from '@/hooks/useSeeds';
import { buscarMovimentacoesApi, Movimentacao, NovaMovimentacao, registrarMovimentacaoApi } from '@/lib/estoqueApi';
import { OrigemMovimentacaoLabels, TipoMovimentacao, TipoMovimentacaoLabels } from '@/types/stock';
import styles from './movimentacoes.module.css';

const TIPOS_LANCAVEL: TipoMovimentacao[] = [
  TipoMovimentacao.ENTRADA,
  TipoMovimentacao.SAIDA_VENDA,
  TipoMovimentacao.SAIDA_TROCA,
  TipoMovimentacao.SAIDA_DOACAO,
];

export default function MovimentacoesEstoquePage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { getSeed, recarregar } = useSeeds();
  const { showToast } = useToast();

  const [seedName, setSeedName] = useState<string>('Produto');
  const [saldoAtual, setSaldoAtual] = useState<number | null>(null);
  const [movimentacoes, setMovimentacoes] = useState<Movimentacao[]>([]);
  const [loading, setLoading] = useState(true);

  // Formulário de lançamento rápido
  const [showForm, setShowForm] = useState(false);
  const [tipo, setTipo] = useState<TipoMovimentacao>(TipoMovimentacao.ENTRADA);
  const [quantidade, setQuantidade] = useState('');
  const [descricao, setDescricao] = useState('');
  const [salvando, setSalvando] = useState(false);

  const carregar = useCallback(async () => {
    try {
      const pagina = await buscarMovimentacoesApi(id, 0, 50);
      setMovimentacoes(pagina.content);
      // O saldo vigente é o da linha mais recente: o endpoint devolve do mais
      // novo para o mais antigo, então o saldoPosterior da primeira linha é
      // o saldo de agora.
      setSaldoAtual(pagina.content[0]?.saldoPosterior ?? null);
    } catch {
      showToast('Não foi possível carregar o histórico.', 'error');
    } finally {
      setLoading(false);
    }
  }, [id, showToast]);

  useEffect(() => {
    getSeed(id).then((s) => {
      if (s) {
        setSeedName(s.nomePopular);
        setSaldoAtual(s.quantidade);
      }
    });
    carregar();
  }, [id, getSeed, carregar]);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const qtd = Number(quantidade);
    if (!qtd || qtd <= 0) {
      showToast('Informe uma quantidade maior que zero.', 'error');
      return;
    }
    setSalvando(true);
    try {
      await registrarMovimentacaoApi(id, {
        tipo,
        quantidade: qtd,
        descricao: descricao.trim() || undefined,
      });
      showToast('Movimentação registrada.', 'success');
      setShowForm(false);
      setQuantidade('');
      setDescricao('');
      await Promise.all([carregar(), recarregar()]);
    } catch (err) {
      showToast(
        err instanceof Error ? err.message : 'Não foi possível registrar a movimentação.',
        'error'
      );
    } finally {
      setSalvando(false);
    }
  };

  return (
    <div className={styles.page}>
      <div className={styles.header}>
        <Button variant="ghost" onClick={() => router.back()} className={styles.backBtn}>
          <ArrowLeft size={20} />
        </Button>
        <div className={styles.headerInfo}>
          <h1 className={styles.title}>Histórico de Estoque</h1>
          <p className={styles.subtitle}>
            {seedName}
            {saldoAtual !== null && ` · saldo ${saldoAtual} `}
          </p>
        </div>
      </div>

      <Button variant="secondary" fullWidth onClick={() => setShowForm((v) => !v)}>
        <Plus size={16} strokeWidth={2.5} />
        {showForm ? 'Cancelar' : 'Nova movimentação'}
      </Button>

      {showForm && (
        <form onSubmit={submit} className={styles.form}>
          <div className={styles.formRow}>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Tipo</span>
              <select
                value={tipo}
                onChange={(e) => setTipo(e.target.value as TipoMovimentacao)}
                className={styles.select}
              >
                {TIPOS_LANCAVEL.map((t) => (
                  <option key={t} value={t}>
                    {TipoMovimentacaoLabels[t]}
                  </option>
                ))}
              </select>
            </label>
            <label className={styles.field}>
              <span className={styles.fieldLabel}>Quantidade</span>
              <input
                type="number"
                inputMode="decimal"
                min="0.01"
                step="0.01"
                value={quantidade}
                onChange={(e) => setQuantidade(e.target.value)}
                placeholder="0"
                required
                className={styles.input}
              />
            </label>
          </div>
          <label className={styles.field}>
            <span className={styles.fieldLabel}>Motivo (opcional)</span>
            <input
              type="text"
              value={descricao}
              onChange={(e) => setDescricao(e.target.value)}
              placeholder="Ex.: colheita da safra 2026"
              className={styles.input}
            />
          </label>
          <Button type="submit" variant="primary" fullWidth loading={salvando}>
            Registrar
          </Button>
        </form>
      )}

      {loading ? (
        <div className={styles.list}>
          {[1, 2, 3].map((i) => (
            <div key={i} className="skeleton" style={{ height: 70, borderRadius: 12 }} />
          ))}
        </div>
      ) : movimentacoes.length === 0 ? (
        <EmptyState
          icon={<History size={38} strokeWidth={1.5} />}
          title="Sem movimentações"
          description="Ainda não há registros de entrada ou saída para este produto."
        />
      ) : (
        <ul className={styles.list}>
          {movimentacoes.map((mov) => {
            const sinal = mov.aumento ? '+' : '−';
            const data = new Date(mov.dataMovimentacao);
            return (
              <li key={mov.id} className={styles.card}>
                <div className={`${styles.iconWrap} ${mov.aumento ? styles.iconPos : styles.iconNeg}`}>
                  {mov.aumento ? (
                    <ArrowUp size={16} strokeWidth={2.5} />
                  ) : (
                    <ArrowDown size={16} strokeWidth={2.5} />
                  )}
                </div>
                <div className={styles.info}>
                  <p className={styles.type}>
                    {TipoMovimentacaoLabels[mov.tipo]}
                    <span className={styles.origem}>{OrigemMovimentacaoLabels[mov.origem]}</span>
                  </p>
                  <p className={styles.date}>
                    {data.toLocaleDateString()} às{' '}
                    {data.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                  </p>
                  {mov.descricao && <p className={styles.motivo}>{mov.descricao}</p>}
                  <p className={styles.saldo}>
                    saldo {mov.saldoAnterior} → {mov.saldoPosterior}
                    {mov.usuarioNome && ` · ${mov.usuarioNome}`}
                  </p>
                </div>
                <div className={`${styles.qty} ${mov.aumento ? styles.qtyPos : styles.qtyNeg}`}>
                  {sinal}
                  {mov.quantidade}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );
}
