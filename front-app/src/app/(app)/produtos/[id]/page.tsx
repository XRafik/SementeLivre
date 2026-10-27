'use client';

import React, { useEffect, useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { Sprout, BarChart2, List, Pencil, Trash2, PackageSearch, ShoppingCart, ChevronRight } from 'lucide-react';
import { useSeeds } from '@/hooks/useSeeds';
import { useOrders } from '@/hooks/useOrders';
import { useToast } from '@/components/feedback/Toast';
import { Estoque, DisponibilidadeLabels, DisponibilidadeProduto, PesagemLabels, TipoMovimentacao, TipoNegociavelLabels } from '@/types/stock';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Toggle } from '@/components/ui/toggle';
import { ConfirmDialog } from '@/components/shared/ConfirmDialog';
import { EmptyState } from '@/components/feedback/EmptyState';
import Link from 'next/link';
import styles from './semente.module.css';

const pesagemOptions = Object.entries(PesagemLabels).map(([v, l]) => ({ value: v, label: l }));
const dispOptions = Object.entries(DisponibilidadeLabels).map(([v, l]) => ({ value: v, label: l }));

export default function SementeDetailPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const { getSeed, updateSeed, deleteSeed } = useSeeds();
  const { showToast } = useToast();
  const { orders } = useOrders();

  const [seed, setSeed] = useState<Estoque | null>(null);
  const [loading, setLoading] = useState(true);
  const [editing, setEditing] = useState(false);
  const [editForm, setEditForm] = useState<Partial<Estoque>>({});
  const [showDelete, setShowDelete] = useState(false);
  const [showAjuste, setShowAjuste] = useState(false);
  const [ajusteDir, setAjusteDir] = useState<'Adicionar' | 'Subtrair'>('Adicionar');
  const [ajusteQty, setAjusteQty] = useState('');
  const [saving, setSaving] = useState(false);
  const [deleting, setDeleting] = useState(false);

  useEffect(() => {
    getSeed(id).then((s) => { setSeed(s); setLoading(false); });
  }, [id, getSeed]);

  if (loading) return <div className={styles.loading}><div className="skeleton" style={{ height: 200, borderRadius: 16 }} /></div>;
  if (!seed) return <EmptyState icon={<PackageSearch size={38} strokeWidth={1.5} />} title="Produto nao encontrado" actionLabel="Voltar" onAction={() => router.push('/produtos')} />;

  const disponibilidadeAtual = (editForm.disponibilidade ?? seed.disponibilidade) as DisponibilidadeProduto;
  const tipoNegociavelEdicao = TipoNegociavelLabels[disponibilidadeAtual];
  const aceitaNegociacaoEdicao = editForm.aceitaNegociacao ?? seed.aceitaNegociacao ?? true;
  const tipoNegociavel = TipoNegociavelLabels[seed.disponibilidade];

  const handleSave = async () => {
    setSaving(true);
    try {
      await updateSeed(id, editForm);
      setSeed((prev) => prev ? { ...prev, ...editForm } : prev);
      setEditing(false);
      showToast('Produto atualizado!', 'success');
    } catch { showToast('Erro ao salvar.', 'error'); }
    finally { setSaving(false); }
  };

  const handleDelete = async () => {
    setDeleting(true);
    try {
      await deleteSeed(id);
      showToast('Produto removido.', 'success');
      router.push('/produtos');
    } catch { showToast('Erro ao excluir.', 'error'); }
    finally { setDeleting(false); }
  };

  const handleAjuste = async () => {
    const delta = Number(ajusteQty);
    if (!delta || delta <= 0) return;
    const novaQty = ajusteDir === 'Adicionar' ? seed.quantidade + delta : seed.quantidade - delta;
    if (novaQty < 0) { showToast('Estoque nao pode ser negativo.', 'error'); return; }
    setSaving(true);
    try {
      await updateSeed(id, { quantidade: novaQty });
      setSeed((prev) => prev ? { ...prev, quantidade: novaQty } : prev);
      setShowAjuste(false);
      setAjusteQty('');
      showToast('Estoque ajustado!', 'success');
    } catch { showToast('Erro ao ajustar.', 'error'); }
    finally { setSaving(false); }
  };

  return (
    <div className={styles.page}>
      {/* Foto */}
      <div className={styles.photoHero}>
        {seed.urlFoto ? (
          <img src={seed.urlFoto} alt={`Foto de ${seed.nomePopular}`} className={styles.heroImg} />
        ) : (
          <div className={styles.heroPlaceholder}>
            <Sprout size={48} strokeWidth={1.5} />
          </div>
        )}
      </div>

      {editing ? (
        <div className={styles.editForm}>
          <Input label="Nome popular" value={editForm.nomePopular ?? seed.nomePopular} onChange={(e) => setEditForm((p) => ({ ...p, nomePopular: e.target.value }))} required />
          
          <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
            <div style={{ flex: 1 }}>
              <Input label="Quantidade" type="number" value={String(editForm.quantidade ?? seed.quantidade)} onChange={(e) => setEditForm((p) => ({ ...p, quantidade: Number(e.target.value) }))} inputMode="decimal" />
            </div>
            <div style={{ flex: 1 }}>
              <Select label="Unidade" value={editForm.tipoPesagem ?? seed.tipoPesagem} onChange={(e) => setEditForm((p) => ({ ...p, tipoPesagem: e.target.value as any }))} options={pesagemOptions} />
            </div>
          </div>
          
          <Select label="Disponibilidade" value={editForm.disponibilidade ?? seed.disponibilidade} onChange={(e) => setEditForm((p) => ({ ...p, disponibilidade: e.target.value as any }))} options={dispOptions} />

          {tipoNegociavelEdicao && (
            <div>
              <Toggle
                label="Negociação no site"
                options={[
                  { value: 'sim', label: 'Aceito propostas' },
                  { value: 'nao', label: `Só ${tipoNegociavelEdicao}` },
                ]}
                value={aceitaNegociacaoEdicao ? 'sim' : 'nao'}
                onChange={(v) => setEditForm((p) => ({ ...p, aceitaNegociacao: v === 'sim' }))}
              />
              <p className={styles.hint}>
                {aceitaNegociacaoEdicao
                  ? 'Quem pede pelo site pode propor venda, troca ou doação.'
                  : `Quem pede pelo site só consegue fazer pedido de ${tipoNegociavelEdicao}.`}
              </p>
            </div>
          )}
          
          <Input label="Preço (R$)" type="number" value={editForm.preco !== undefined ? String(editForm.preco) : String(seed.preco ?? '')} onChange={(e) => setEditForm((p) => ({ ...p, preco: e.target.value ? Number(e.target.value) : undefined }))} inputMode="decimal" step="0.01" placeholder="Opcional" />
          
          <Textarea label="Descrição" value={editForm.descricao ?? (seed.descricao || '')} onChange={(e) => setEditForm((p) => ({ ...p, descricao: e.target.value }))} placeholder="Opcional" />
          
          <div style={{ display: 'flex', gap: 'var(--space-3)' }}>
            <Button variant="ghost" onClick={() => setEditing(false)} fullWidth>Cancelar</Button>
            <Button variant="primary" onClick={handleSave} loading={saving} fullWidth>Salvar</Button>
          </div>
        </div>
      ) : (
        <>
          <div className={styles.info}>
            <div className={styles.titleRow}>
              <h2 className={styles.name}>{seed.nomePopular}</h2>
              <Badge variant="availability" value={seed.disponibilidade} />
            </div>
            {seed.descricao && <p className={styles.desc}>{seed.descricao}</p>}

            <div className={styles.grid}>
              <div className={styles.gridItem}>
                <span className={styles.gridLabel}>Quantidade</span>
                <span className={styles.gridValue}>{seed.quantidade} {PesagemLabels[seed.tipoPesagem] || seed.tipoPesagem}</span>
              </div>
              {seed.preco && (
                <div className={styles.gridItem}>
                  <span className={styles.gridLabel}>Preco</span>
                  <span className={styles.gridValue}>R$ {seed.preco.toFixed(2)}</span>
                </div>
              )}
              {tipoNegociavel && (
                <div className={styles.gridItem}>
                  <span className={styles.gridLabel}>Negociação no site</span>
                  <span className={styles.gridValue}>
                    {seed.aceitaNegociacao === false ? `Só ${tipoNegociavel}` : 'Aceita propostas'}
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* Estoque actions */}
          <div className={styles.stockSection}>
            <Button variant="secondary" fullWidth onClick={() => setShowAjuste(true)}>
              <BarChart2 size={16} strokeWidth={2} />
              Ajustar Estoque
            </Button>
            <Link href={`/produtos/${id}/estoque`}>
              <Button variant="ghost" fullWidth>
                <List size={16} strokeWidth={2} />
                Ver Movimentacoes
              </Button>
            </Link>
          </div>

          <div className={styles.actions}>
            <Button variant="ghost" fullWidth onClick={() => { setEditing(true); setEditForm({}); }}>
              <Pencil size={15} strokeWidth={2} />
              Editar
            </Button>
            <Button variant="danger" fullWidth onClick={() => setShowDelete(true)}>
              <Trash2 size={15} strokeWidth={2} />
              Excluir
            </Button>
          </div>

          {/* Pedidos vinculados */}
          {orders.filter(o => o.itens.some(i => i.idProduto === id)).length > 0 && (
            <div className={styles.ordersSection}>
              <h3 className={styles.sectionTitle}>Pedidos com este produto</h3>
              <ul className={styles.orderList}>
                {orders.filter(o => o.itens.some(i => i.idProduto === id)).map((order) => (
                  <li key={order.idPedido}>
                    <Link href={`/pedidos/${order.idPedido}`} className={styles.orderCard}>
                      <div className={styles.orderIcon}>
                        <ShoppingCart size={16} strokeWidth={2} />
                      </div>
                      <div className={styles.orderInfo}>
                        <span className={styles.orderTo}>{order.nomeRecebedor}</span>
                        <span className={styles.orderDate}>{order.dataPedido.toLocaleDateString()}</span>
                      </div>
                      <ChevronRight size={16} className={styles.chevron} />
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </>
      )}

      {/* Ajuste dialog */}
      <Dialog isOpen={showAjuste} onClose={() => setShowAjuste(false)} title="Ajustar Estoque">
        <p className={styles.dialogInfo}>Estoque atual: <strong>{seed.quantidade} {PesagemLabels[seed.tipoPesagem]}</strong></p>
        <Toggle options={[{ value: 'Adicionar', label: 'Adicionar' }, { value: 'Subtrair', label: 'Subtrair', variant: 'danger' }]} value={ajusteDir} onChange={(v) => setAjusteDir(v as 'Adicionar' | 'Subtrair')} />
        <Input label="Quantidade" value={ajusteQty} onChange={(e) => setAjusteQty(e.target.value)} type="number" inputMode="decimal" min="0" />
        <div style={{ display: 'flex', gap: 'var(--space-3)', marginTop: 'var(--space-4)' }}>
          <Button variant="ghost" onClick={() => setShowAjuste(false)} fullWidth>Cancelar</Button>
          <Button variant={ajusteDir === 'Subtrair' ? 'danger' : 'primary'} onClick={handleAjuste} loading={saving} fullWidth>Confirmar</Button>
        </div>
      </Dialog>

      {/* Delete confirm */}
      <ConfirmDialog
        isOpen={showDelete}
        title="Excluir Produto"
        description="Tem certeza? O produto sera removido, mas os pedidos anteriores serao mantidos."
        confirmLabel="Excluir"
        confirmVariant="danger"
        onConfirm={handleDelete}
        onCancel={() => setShowDelete(false)}
        loading={deleting}
      />
    </div>
  );
}
