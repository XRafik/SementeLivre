'use client';

import React, { useState } from 'react';
import { useSeeds } from '@/hooks/useSeeds';
import { useOrders } from '@/hooks/useOrders';
import { useRelatorios } from '@/hooks/useRelatorios';
import { isApiError } from '@/lib/api';
import { Toggle } from '@/components/ui/toggle';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { DisponibilidadeLabels } from '@/types/stock';
import { TipoPedidoLabels, StatusPedidoLabels } from '@/types/order';
import { EmptyState } from '@/components/feedback/EmptyState';
import { useToast } from '@/components/feedback/Toast';
import styles from './relatorios.module.css';

type ReportType = 'ESTOQUE' | 'PEDIDOS';

export default function RelatoriosPage() {
  const { seeds } = useSeeds();
  const { orders } = useOrders();
  const { relatorios, registrarEmissao } = useRelatorios();
  const { showToast } = useToast();

  const [tipo, setTipo] = useState<ReportType>('ESTOQUE');
  const [dataInicio, setDataInicio] = useState('');
  const [dataFim, setDataFim] = useState('');
  const [filtroDisp, setFiltroDisp] = useState('TODOS');
  const [filtroStatus, setFiltroStatus] = useState('TODOS');
  const [generated, setGenerated] = useState(false);
  const [gerando, setGerando] = useState(false);
  const [exportando, setExportando] = useState(false);

  const tipoOptions = [{ value: 'ESTOQUE', label: '📊 Estoque' }, { value: 'PEDIDOS', label: '📋 Pedidos' }];
  const dispOptions = [{ value: 'TODOS', label: 'Todas' }, ...Object.entries(DisponibilidadeLabels).map(([v, l]) => ({ value: v, label: l }))];
  const statusOptions = [{ value: 'TODOS', label: 'Todos' }, ...Object.entries(StatusPedidoLabels).map(([v, l]) => ({ value: v, label: l }))];

  const filteredSeeds = seeds.filter((s) => filtroDisp === 'TODOS' || s.disponibilidade === filtroDisp);
  const filteredOrders = orders.filter((o) => {
    const matchStatus = filtroStatus === 'TODOS' || o.status === filtroStatus;
    const dt = o.dataPedido;
    const matchStart = !dataInicio || dt >= new Date(dataInicio);
    const matchEnd = !dataFim || dt <= new Date(dataFim + 'T23:59:59');
    return matchStatus && matchStart && matchEnd;
  });

  const data = tipo === 'ESTOQUE' ? filteredSeeds : filteredOrders;

  // O histórico da tela mostra só os dois tipos que este formulário emite.
  const historico = relatorios.filter(
    (r) => r.tipo === (tipo === 'ESTOQUE' ? 'ESTOQUE_SEMENTES' : 'PEDIDOS_REALIZADOS')
  );

  /** Gera a prévia e registra a emissão em /relatorios (histórico no backend). */
  const handleGenerate = async () => {
    setGerando(true);
    try {
      await registrarEmissao(
        tipo === 'ESTOQUE' ? 'ESTOQUE_SEMENTES' : 'PEDIDOS_REALIZADOS',
        {
          tipo,
          filtroDisponibilidade: filtroDisp,
          filtroStatus: filtroStatus,
          dataInicio: dataInicio || null,
          dataFim: dataFim || null,
          totalRegistros: data.length,
        }
      );
      setGenerated(true);
      showToast('Relatório gerado e registrado!', 'success');
    } catch (err) {
      showToast(
        isApiError(err) ? err.message : 'Não foi possível registrar o relatório.',
        'error'
      );
    } finally {
      setGerando(false);
    }
  };

  /** Gera o PDF no cliente; a emissão já foi registrada em /relatorios por handleGenerate. */
  const handleExportPDF = async () => {
    if (!data.length) return;
    setExportando(true);
    try {
      const { baixarRelatorioPdf } = await import('@/lib/pdf');
      await baixarRelatorioPdf({
        tipo,
        filtroDisponibilidade: filtroDisp,
        filtroStatus,
        dataInicio,
        dataFim,
        seeds: filteredSeeds,
        orders: filteredOrders,
      });
      showToast('Relatório PDF exportado!', 'success');
    } catch {
      showToast('Não foi possível gerar o PDF.', 'error');
    } finally {
      setExportando(false);
    }
  };

  const handleExportCSV = () => {
    if (!data.length) return;
    let csv = '';
    if (tipo === 'ESTOQUE') {
      csv = 'Nome,Quantidade,Unidade,Disponibilidade,Preço\n';
      (filteredSeeds).forEach((s) => {
        csv += `"${s.nomePopular}",${s.quantidade},${s.tipoPesagem},"${DisponibilidadeLabels[s.disponibilidade]}",${s.preco || ''}\n`;
      });
    } else {
      csv = 'ID,Data,Recebedor,Tipo,Status,Total\n';
      (filteredOrders).forEach((o) => {
        csv += `"${o.idPedido.slice(-6)}","${o.dataPedido.toLocaleDateString('pt-BR')}","${o.nomeRecebedor}","${TipoPedidoLabels[o.tipoPedido]}","${StatusPedidoLabels[o.status]}",${o.totalValor || ''}\n`;
      });
    }
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `relatorio-${tipo.toLowerCase()}-${Date.now()}.csv`;
    a.click();
    showToast('Relatório CSV exportado!', 'success');
  };

  return (
    <div className={styles.page}>
      <div className={styles.card}>
        <p className={styles.cardTitle}>Tipo de Relatório</p>
        <Toggle options={tipoOptions} value={tipo} onChange={(v) => { setTipo(v as ReportType); setGenerated(false); }} />
      </div>

      <div className={styles.card}>
        <p className={styles.cardTitle}>Filtros</p>
        {tipo === 'ESTOQUE' ? (
          <div className={styles.filters} role="group" aria-label="Filtrar por disponibilidade">
            {dispOptions.map((f) => (
              <button key={f.value} className={`${styles.chip} ${filtroDisp === f.value ? styles.chipActive : ''}`} onClick={() => setFiltroDisp(f.value)} aria-pressed={filtroDisp === f.value}>{f.label}</button>
            ))}
          </div>
        ) : (
          <>
            <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
              <Input label="Data início" type="date" value={dataInicio} onChange={(e) => setDataInicio(e.target.value)} />
              <Input label="Data fim" type="date" value={dataFim} onChange={(e) => setDataFim(e.target.value)} />
            </div>
            <div className={styles.filters} role="group" aria-label="Filtrar por status">
              {statusOptions.map((f) => (
                <button key={f.value} className={`${styles.chip} ${filtroStatus === f.value ? styles.chipActive : ''}`} onClick={() => setFiltroStatus(f.value)} aria-pressed={filtroStatus === f.value}>{f.label}</button>
              ))}
            </div>
          </>
        )}
        <Button variant="primary" fullWidth onClick={handleGenerate} loading={gerando}>
          Gerar Relatório
        </Button>
      </div>

      <div className={styles.card}>
        <div className={styles.previewHeader}>
          <p className={styles.cardTitle}>{generated ? `Prévia — ${data.length} registros` : 'Exportar relatório'}</p>
          <div className={styles.previewActions}>
            <Button variant="secondary" size="sm" onClick={handleExportPDF} disabled={data.length === 0} loading={exportando}>⬇ PDF</Button>
            <Button variant="secondary" size="sm" onClick={handleExportCSV} disabled={data.length === 0}>⬇ CSV</Button>
          </div>
        </div>

        {!generated ? (
          <EmptyState
            icon="📋"
            title="Nenhum relatório gerado"
            description="Ajuste os filtros e clique em Gerar Relatório para ver a prévia e baixar em PDF ou CSV."
          />
        ) : data.length === 0 ? (
          <EmptyState icon="📋" title="Sem dados" description="Nenhum registro para os filtros selecionados." />
        ) : tipo === 'ESTOQUE' ? (
            <div className={styles.tableWrap}>
              <table className={styles.table} aria-label="Relatório de estoque">
                <thead>
                  <tr><th>Semente</th><th>Qtd.</th><th>Unid.</th><th>Disp.</th></tr>
                </thead>
                <tbody>
                  {filteredSeeds.map((s) => (
                    <tr key={s.idEstoque}>
                      <td>{s.nomePopular}</td>
                      <td>{s.quantidade}</td>
                      <td>{s.tipoPesagem}</td>
                      <td><Badge variant="availability" value={s.disponibilidade} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <div className={styles.tableWrap}>
              <table className={styles.table} aria-label="Relatório de pedidos">
                <thead>
                  <tr><th>ID</th><th>Data</th><th>Recebedor</th><th>Tipo</th><th>Status</th></tr>
                </thead>
                <tbody>
                  {filteredOrders.map((o) => (
                    <tr key={o.idPedido}>
                      <td style={{ fontFamily: 'monospace', fontSize: '0.7rem' }}>#{o.idPedido.slice(-6).toUpperCase()}</td>
                      <td>{o.dataPedido.toLocaleDateString('pt-BR')}</td>
                      <td>{o.nomeRecebedor}</td>
                      <td><Badge variant="orderType" value={o.tipoPedido} /></td>
                      <td><Badge variant="orderStatus" value={o.status} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
      </div>

      <div className={styles.card}>
        <p className={styles.cardTitle}>Relatórios emitidos</p>
        {historico.length === 0 ? (
          <EmptyState icon="🗂" title="Nenhum relatório emitido" description="Gere um relatório acima para registrar o histórico." />
        ) : (
          <div className={styles.tableWrap}>
            <table className={styles.table} aria-label="Histórico de relatórios emitidos">
              <thead>
                <tr><th>Data</th><th>Tipo</th><th>Registros</th></tr>
              </thead>
              <tbody>
                {historico.map((r) => (
                  <tr key={r.idRelatorio}>
                    <td>{r.dataGeracao.toLocaleString('pt-BR')}</td>
                    <td>{r.tipo === 'ESTOQUE_SEMENTES' ? 'Estoque de sementes' : 'Pedidos realizados'}</td>
                    <td>{String(r.filtros.totalRegistros ?? '—')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}
