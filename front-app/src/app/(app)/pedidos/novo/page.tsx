'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useOrders } from '@/hooks/useOrders';
import { EstoqueParaPedido, useOrderStock } from '@/hooks/useOrderStock';
import { useAuth } from '@/context/AuthContext';
import { useNotifications } from '@/context/NotificationContext';
import { useToast } from '@/components/feedback/Toast';
import { errorMessage } from '@/lib/api';
import { TipoPedido } from '@/types/order';
import { DisponibilidadeProduto, PesagemLabels } from '@/types/stock';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Toggle } from '@/components/ui/toggle';
import { Button } from '@/components/ui/button';
import { Dialog } from '@/components/ui/dialog';
import { Badge } from '@/components/ui/badge';
import styles from './novoPedido.module.css';

interface ItemForm { seed: EstoqueParaPedido; quantidade: number; }

const tipoOptions = [
  { value: TipoPedido.VENDA, label: 'Venda' },
  { value: TipoPedido.TROCA, label: 'Troca' },
  { value: TipoPedido.DOACAO, label: 'Doação' },
];

export default function NovoPedidoPage() {
  const { user } = useAuth();
  const { createOrder } = useOrders();
  const { refresh: refreshNotifications } = useNotifications();
  const { stock, loading: loadingStock, error: stockError, reload: reloadStock } = useOrderStock();
  const { showToast } = useToast();
  const router = useRouter();

  const [tipo, setTipo] = useState<TipoPedido>(TipoPedido.VENDA);
  const [nomeRecebedor, setNomeRecebedor] = useState('');
  const [contato, setContato] = useState('');
  const [mensagem, setMensagem] = useState('');
  const [itens, setItens] = useState<ItemForm[]>([]);
  const [showSeedPicker, setShowSeedPicker] = useState(false);
  const [pickQty, setPickQty] = useState('');
  const [selectedSeed, setSelectedSeed] = useState<EstoqueParaPedido | null>(null);
  const [loading, setLoading] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});

  const availableSeeds = stock.filter(s => s.disponibilidade !== DisponibilidadeProduto.INDISPONIVEL && s.quantidade > 0);
  const total = itens.reduce((sum, i) => sum + (i.seed.preco || 0) * i.quantidade, 0);

  const addItem = () => {
    if (!selectedSeed || !pickQty || Number(pickQty) <= 0) return;
    if (Number(pickQty) > selectedSeed.quantidade) { showToast('Quantidade maior que o estoque disponível.', 'error'); return; }
    setItens(prev => {
      const exists = prev.find(i => i.seed.idEstoque === selectedSeed.idEstoque);
      if (exists) return prev.map(i => i.seed.idEstoque === selectedSeed.idEstoque ? { ...i, quantidade: Number(pickQty) } : i);
      return [...prev, { seed: selectedSeed, quantidade: Number(pickQty) }];
    });
    setShowSeedPicker(false);
    setPickQty('');
    setSelectedSeed(null);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const e2: Record<string, string> = {};
    if (!nomeRecebedor) e2.nomeRecebedor = 'Nome é obrigatório';
    if (itens.length === 0) e2.itens = 'Adicione ao menos um item';
    setErrors(e2);
    if (Object.keys(e2).length > 0) return;
    if (!user) { showToast('Perfil de proprietário não encontrado.', 'error'); return; }
    setLoading(true);
    try {
      const id = await createOrder({
        tipoPedido: tipo,
        nomeRecebedor,
        contatoRecebedor: contato,
        mensagemOpcional: mensagem,
        itens: itens.map(i => ({
          idProduto: i.seed.idProduto,
          quantidade: i.quantidade,
          // So venda tem preco; troca/doacao vao sem valor
          precoUnitario: tipo === TipoPedido.VENDA ? i.seed.preco : undefined,
        })),
      });
      showToast('Pedido registrado com sucesso!', 'success');
      // O registro gera a notificacao "Novo pedido recebido" no backend (CDU-26)
      refreshNotifications();
      router.push(`/pedidos/${id}`);
    } catch (err) {
      // Ex.: estoque insuficiente (422) quando outro pedido reservou antes
      showToast(errorMessage(err, 'Erro ao registrar pedido.'), 'error');
      reloadStock();
    }
    finally { setLoading(false); }
  };

  return (
    <form onSubmit={handleSubmit} className={styles.form} noValidate>
      <div className={styles.card}>
        <p className={styles.sectionTitle}>Tipo de Pedido</p>
        <Toggle options={tipoOptions} value={tipo} onChange={(v) => setTipo(v as TipoPedido)} />
      </div>

      <div className={styles.card}>
        <p className={styles.sectionTitle}>Recebedor</p>
        <Input label="Nome completo" value={nomeRecebedor} onChange={(e) => setNomeRecebedor(e.target.value)} error={errors.nomeRecebedor} required />
        <Input label="Contato (telefone ou e-mail)" value={contato} onChange={(e) => setContato(e.target.value)} />
      </div>

      <div className={styles.card}>
        <div className={styles.sectionHeader}>
          <p className={styles.sectionTitle}>Sementes</p>
          <Button type="button" variant="secondary" size="sm" onClick={() => setShowSeedPicker(true)}>+ Adicionar</Button>
        </div>
        {errors.itens && <span className={styles.error} role="alert">⚠ {errors.itens}</span>}
        {itens.length > 0 && (
          <ul className={styles.itensList}>
            {itens.map((item) => (
              <li key={item.seed.idEstoque} className={styles.itemRow}>
                <span className={styles.itemName}>{item.seed.nomePopular}</span>
                <span className={styles.itemQty}>{item.quantidade} {PesagemLabels[item.seed.tipoPesagem]}</span>
                {tipo === TipoPedido.VENDA && item.seed.preco && (
                  <span className={styles.itemPrice}>R$ {(item.seed.preco * item.quantidade).toFixed(2)}</span>
                )}
                <button type="button" className={styles.removeItem} onClick={() => setItens(p => p.filter(i => i.seed.idEstoque !== item.seed.idEstoque))} aria-label={`Remover ${item.seed.nomePopular}`}>✕</button>
              </li>
            ))}
          </ul>
        )}
        {tipo === TipoPedido.VENDA && itens.length > 0 && (
          <p className={styles.total}>Total: <strong>R$ {total.toFixed(2)}</strong></p>
        )}
      </div>

      <div className={styles.card}>
        <Textarea label="Observações (opcional)" value={mensagem} onChange={(e) => setMensagem(e.target.value)} placeholder="Informações adicionais sobre o pedido..." />
      </div>

      <Button type="submit" fullWidth size="lg" loading={loading}>Registrar Pedido</Button>

      {/* Seed Picker Dialog */}
      <Dialog isOpen={showSeedPicker} onClose={() => { setShowSeedPicker(false); setSelectedSeed(null); setPickQty(''); }} title="Selecionar Semente">
        <div className={styles.seedList}>
          {loadingStock && <p className={styles.seedStock}>Carregando estoque...</p>}
          {stockError && <span className={styles.error} role="alert">⚠ {stockError}</span>}
          {!loadingStock && !stockError && availableSeeds.length === 0 && (
            <p className={styles.seedStock}>Nenhuma semente com estoque disponível.</p>
          )}
          {availableSeeds.map((s) => (
            <button
              key={s.idEstoque}
              type="button"
              className={`${styles.seedOption} ${selectedSeed?.idEstoque === s.idEstoque ? styles.seedSelected : ''}`}
              onClick={() => setSelectedSeed(s)}
            >
              <span className={styles.seedName}>{s.nomePopular}</span>
              <span className={styles.seedStock}>Estoque: {s.quantidade} {PesagemLabels[s.tipoPesagem]}</span>
              <Badge variant="availability" value={s.disponibilidade} />
            </button>
          ))}
        </div>
        {selectedSeed && (
          <div className={styles.qtyRow}>
            <Input label={`Quantidade (máx: ${selectedSeed.quantidade})`} value={pickQty} onChange={(e) => setPickQty(e.target.value)} type="number" inputMode="decimal" min="0" max={String(selectedSeed.quantidade)} />
            <Button type="button" onClick={addItem} variant="primary">Adicionar</Button>
          </div>
        )}
      </Dialog>
    </form>
  );
}
