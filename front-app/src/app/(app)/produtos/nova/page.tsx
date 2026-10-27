'use client';

import React, { useState, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useSeeds } from '@/hooks/useSeeds';
import { useAuth } from '@/context/AuthContext';
import { isApiError } from '@/lib/api';
import { useToast } from '@/components/feedback/Toast';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Toggle } from '@/components/ui/toggle';
import { Button } from '@/components/ui/button';
import { Sprout } from 'lucide-react';
import { TipoProduto, TipoProdutoLabels, EspecieGeral, EspecieGeralLabels, FormatoProduto } from '@/types/seed';
import { Pesagem, PesagemLabels, DisponibilidadeProduto, DisponibilidadeLabels, TipoMovimentacao, TipoNegociavelLabels } from '@/types/stock';
import styles from './nova.module.css';

const tipoOptions = Object.entries(TipoProdutoLabels).map(([v, l]) => ({ value: v, label: l }));
const especieOptions = Object.entries(EspecieGeralLabels).map(([v, l]) => ({ value: v, label: l }));
const pesagemOptions = Object.entries(PesagemLabels).map(([v, l]) => ({ value: v, label: l }));
const dispOptions = Object.entries(DisponibilidadeLabels).map(([v, l]) => ({ value: v, label: l }));
const formatoOptions = [{ value: FormatoProduto.SEMENTE, label: 'Semente' }, { value: FormatoProduto.MUDA, label: 'Muda' }];

export default function NovaSementePage() {
  const router = useRouter();
  const { user } = useAuth();
  const { createSeed, uploadSeedPhoto } = useSeeds();
  const { showToast } = useToast();
  const fileRef = useRef<HTMLInputElement>(null);

  const [loading, setLoading] = useState(false);
  const [fotoFile, setFotoFile] = useState<File | null>(null);
  const [fotoPreview, setFotoPreview] = useState<string>('');
  const [errors, setErrors] = useState<Record<string, string>>({});

  const [form, setForm] = useState({
    nomePopular: '', nomeCientifico: '', historico: '',
    tipo: TipoProduto.HORTALICA, especie: EspecieGeral.OUTRAS, formato: FormatoProduto.SEMENTE,
    quantidade: '', tipoPesagem: Pesagem.KG,
    disponibilidade: DisponibilidadeProduto.PARA_VENDA, preco: '', formaPrecificacao: '', descricao: '',
    aceitaNegociacao: 'sim',
  });

  const set = (field: string, value: string) => setForm((prev) => ({ ...prev, [field]: value }));

  const handleFoto = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;
    setFotoFile(file);
    setFotoPreview(URL.createObjectURL(file));
  };

  const validate = () => {
    const e: Record<string, string> = {};
    if (!form.nomePopular) e.nomePopular = 'Nome popular é obrigatório';
    if (!fotoFile) e.foto = 'Foto é obrigatória';
    if (!form.quantidade || Number(form.quantidade) < 0) e.quantidade = 'Quantidade inválida';
    setErrors(e);
    return Object.keys(e).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate() || !user) return;
    setLoading(true);
    try {
      const tempId = `${user.uid}_${Date.now()}`;
      const urlFoto = fotoFile ? await uploadSeedPhoto(fotoFile, tempId) : '';
      const qty = Number(form.quantidade);
      const dispFinal = qty === 0 ? DisponibilidadeProduto.INDISPONIVEL : form.disponibilidade as DisponibilidadeProduto;

      await createSeed({
        idProprietario: user.uid,
        idProduto: tempId,
        nomePopular: form.nomePopular,
        urlFoto,
        // Campos de /produtos: o backend exige especie e formato, e o formulário
        // já os coletava — antes eram descartados aqui.
        tipoProduto: form.tipo,
        especie: form.especie,
        formato: form.formato,
        nomeCientifico: form.nomeCientifico,
        historico: form.historico,
        descricao: form.descricao,
        preco: form.preco ? Number(form.preco) : undefined,
        formaPrecificacao: form.formaPrecificacao,
        quantidade: qty,
        tipoPesagem: form.tipoPesagem as Pesagem,
        disponibilidade: dispFinal,
        aceitaNegociacao: form.aceitaNegociacao === 'sim',
        tipo: TipoMovimentacao.ENTRADA,
        dataMovimentacao: new Date(),
        dataUltimaAtualizacaoEstoque: new Date(),
      });
      showToast('Produto cadastrado com sucesso!', 'success');
      router.push('/produtos');
    } catch (err) {
      showToast(
        isApiError(err)
          ? Object.values(err.fieldErrors)[0] ?? err.message
          : 'Erro ao cadastrar produto. Tente novamente.',
        'error'
      );
    } finally {
      setLoading(false);
    }
  };

  const tipoNegociavel = TipoNegociavelLabels[form.disponibilidade as DisponibilidadeProduto];

  const showPreco = form.disponibilidade !== DisponibilidadeProduto.INDISPONIVEL && form.disponibilidade !== DisponibilidadeProduto.PARA_DOACAO;

  return (
    <div className={styles.pageWrap}>
      <div className={styles.pageHeader}>
        <div className={styles.headerIconWrap}>
          <Sprout size={28} strokeWidth={2} />
        </div>
        <div>
          <h1 className={styles.pageTitle}>Novo Produto</h1>
          <p className={styles.pageSubtitle}>Adicione um novo item ao seu estoque</p>
        </div>
      </div>

      <form onSubmit={handleSubmit} className={styles.form} noValidate>

        {/* Foto */}
      <section className={styles.fieldset}>
        <h2 className={styles.legend}>Foto do Produto</h2>
        <div
          className={styles.photoArea}
          onClick={() => fileRef.current?.click()}
          role="button"
          tabIndex={0}
          aria-label="Selecionar foto do produto"
          onKeyDown={(e) => e.key === 'Enter' && fileRef.current?.click()}
        >
          {fotoPreview ? (
            <img src={fotoPreview} alt="Preview do produto" className={styles.photoPreview} />
          ) : (
            <div className={styles.photoPlaceholder}>
              <span aria-hidden="true">📷</span>
              <span>Toque para adicionar foto</span>
            </div>
          )}
        </div>
        <input ref={fileRef} type="file" accept="image/*" capture="environment" onChange={handleFoto} className="sr-only" aria-label="Arquivo de foto" />
        {errors.foto && <span className={styles.error} role="alert">⚠ {errors.foto}</span>}
        {fotoPreview && (
          <Button type="button" variant="text" onClick={() => { setFotoFile(null); setFotoPreview(''); }}>
            Remover foto
          </Button>
        )}
      </section>

      {/* Identificação */}
      <section className={styles.fieldset}>
        <h2 className={styles.legend}>Identificação</h2>
        <Input label="Nome popular" value={form.nomePopular} onChange={(e) => set('nomePopular', e.target.value)} error={errors.nomePopular} required />
        <Input label="Nome científico" value={form.nomeCientifico} onChange={(e) => set('nomeCientifico', e.target.value)} placeholder="Opcional" />
        <Textarea label="Histórico / Descrição" value={form.historico} onChange={(e) => set('historico', e.target.value)} placeholder="Conte um pouco sobre este produto..." />
      </section>

      {/* Classificação */}
      <section className={styles.fieldset}>
        <h2 className={styles.legend}>Classificação</h2>
        <Select label="Tipo" value={form.tipo} onChange={(e) => set('tipo', e.target.value)} options={tipoOptions} required />
        <Select label="Espécie" value={form.especie} onChange={(e) => set('especie', e.target.value)} options={especieOptions} required />
        <Toggle label="Formato" options={formatoOptions} value={form.formato} onChange={(v) => set('formato', v)} />
      </section>

      {/* Estoque */}
      <section className={styles.fieldset}>
        <h2 className={styles.legend}>Estoque Inicial</h2>
        <div className={styles.row}>
          <div style={{ flex: 1 }}>
            <Input label="Quantidade" value={form.quantidade} onChange={(e) => set('quantidade', e.target.value)} error={errors.quantidade} type="number" inputMode="decimal" min="0" required />
          </div>
          <div style={{ flex: 1 }}>
            <Select label="Unidade" value={form.tipoPesagem} onChange={(e) => set('tipoPesagem', e.target.value)} options={pesagemOptions} required />
          </div>
        </div>
        <Select label="Disponibilidade" value={form.disponibilidade} onChange={(e) => set('disponibilidade', e.target.value)} options={dispOptions} required />
        {tipoNegociavel && (
          <div>
            <Toggle
              label="Negociação no site"
              options={[
                { value: 'sim', label: 'Aceito propostas' },
                { value: 'nao', label: `Só ${tipoNegociavel}` },
              ]}
              value={form.aceitaNegociacao}
              onChange={(v) => set('aceitaNegociacao', v)}
            />
            <p className={styles.hint}>
              {form.aceitaNegociacao === 'sim'
                ? 'Quem pede pelo site pode propor venda, troca ou doação.'
                : `Quem pede pelo site só consegue fazer pedido de ${tipoNegociavel}.`}
            </p>
          </div>
        )}
        {showPreco && (
          <>
            <Input label="Preço (R$)" value={form.preco} onChange={(e) => set('preco', e.target.value)} type="number" inputMode="decimal" min="0" step="0.01" placeholder="0,00" />
            <Input label="Forma de precificação" value={form.formaPrecificacao} onChange={(e) => set('formaPrecificacao', e.target.value)} placeholder="Ex: R$ 10,00/kg" />
          </>
        )}
        <Textarea label="Observações" value={form.descricao} onChange={(e) => set('descricao', e.target.value)} placeholder="Informações adicionais..." />
      </section>

      <div className={styles.submitBar}>
        <Button type="submit" fullWidth size="lg" loading={loading} className={styles.submitBtn}>
          Cadastrar Produto
        </Button>
      </div>
    </form>
    </div>
  );
}
