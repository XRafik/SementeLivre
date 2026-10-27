"use client";

import { useEffect, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { toast } from "react-toastify";
import Link from "next/link";
import PublicHeader from "@/components/public-header";
import FotoProduto from "@/components/foto-produto";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Loader2, Leaf, FlaskConical, BookOpen, ArrowLeft, Lock } from "lucide-react";
import type { Oferta, PedidoPublicoRequest, ProdutoDetalhe, TipoPedido } from "@/lib/types";
import {
  DISPONIBILIDADE_CONFIG,
  TIPO_PEDIDO_LABEL,
  TIPO_PRODUTO_LABEL,
  mensagemDeErro,
  unidade,
} from "@/lib/catalogo";

const emptyForm = {
  quantidade: 1,
  nome: "",
  email: "",
  telefone: "",
  mensagemOpcional: "",
};

export default function ProdutoPage() {
  const params = useParams();
  const router = useRouter();
  const id = params.id as string;

  const [produto, setProduto] = useState<ProdutoDetalhe | null>(null);
  const [estoqueId, setEstoqueId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState(emptyForm);
  // null = o tipo da própria oferta; outro valor só quando o produtor aceita negociar
  const [tipoProposto, setTipoProposto] = useState<TipoPedido | null>(null);

  useEffect(() => {
    // A home manda ?oferta=<estoqueId> para abrir já com a oferta escolhida
    const ofertaDaUrl = new URLSearchParams(window.location.search).get("oferta");

    fetch(`/api/catalog/${id}`)
      .then((r) => (r.ok ? r.json() : null))
      .then((p: ProdutoDetalhe | null) => {
        setProduto(p);
        if (p && p.ofertas.length > 0) {
          const escolhida = p.ofertas.find((o) => o.estoqueId === ofertaDaUrl);
          setEstoqueId((escolhida ?? p.ofertas[0]).estoqueId);
        }
      })
      .catch(() => toast.error("Erro ao carregar produto."))
      .finally(() => setLoading(false));
  }, [id]);

  const oferta: Oferta | undefined = produto?.ofertas.find((o) => o.estoqueId === estoqueId);
  const tipoDaOferta = oferta ? DISPONIBILIDADE_CONFIG[oferta.disponibilidade].tipoPedido : null;
  const tipoPedido = oferta?.aceitaNegociacao ? (tipoProposto ?? tipoDaOferta) : tipoDaOferta;

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!oferta || !tipoPedido) return;
    if (!form.nome || !form.email) {
      toast.error("Preencha os campos obrigatórios.");
      return;
    }
    setSubmitting(true);
    try {
      const pedido: PedidoPublicoRequest = {
        estoqueId: oferta.estoqueId,
        quantidade: form.quantidade,
        nome: form.nome,
        email: form.email,
        telefone: form.telefone,
        mensagemOpcional: form.mensagemOpcional,
        tipoPedido,
      };
      const res = await fetch("/api/pedidos", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(pedido),
      });
      if (!res.ok) {
        const erro = await res.json().catch(() => null);
        toast.error(mensagemDeErro(erro, "Erro ao enviar pedido."));
        return;
      }
      toast.success("Pedido enviado! Uma cópia vai para o e-mail informado.");
      router.push("/");
    } catch {
      toast.error("Erro ao enviar pedido.");
    } finally {
      setSubmitting(false);
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-background flex flex-col">
        <PublicHeader />
        <div className="flex-1 flex items-center justify-center">
          <Loader2 className="w-8 h-8 text-primary animate-spin" />
        </div>
      </div>
    );
  }

  if (!produto || !oferta) {
    return (
      <div className="min-h-screen bg-background flex flex-col">
        <PublicHeader />
        <div className="flex-1 flex flex-col items-center justify-center gap-4">
          <Leaf className="w-16 h-16 text-primary/30" />
          <p className="text-muted-foreground">Produto não encontrado.</p>
          <Link href="/">
            <Button variant="outline" className="gap-2 border-border text-primary-dark">
              <ArrowLeft className="w-4 h-4" /> Voltar ao catálogo
            </Button>
          </Link>
        </div>
      </div>
    );
  }

  const statusCfg = DISPONIBILIDADE_CONFIG[oferta.disponibilidade];
  const descricao = oferta.descricao || produto.historico;
  const local = [produto.municipio, produto.uf].filter(Boolean).join(" - ");

  return (
    <div className="min-h-screen bg-background flex flex-col">
      <PublicHeader />

      <main className="flex-1 max-w-4xl mx-auto w-full px-6 py-10 space-y-8">
        <Link href="/" className="inline-flex items-center gap-1.5 text-sm text-primary hover:underline">
          <ArrowLeft className="w-4 h-4" /> Voltar ao catálogo
        </Link>

        {/* ── Product detail ── */}
        <Card className="border-border">
          <div className="flex flex-col md:flex-row gap-6 p-6">
            {/* Photo */}
            <div className="md:w-64 flex-shrink-0 h-48 md:h-auto rounded-xl bg-gradient-to-br from-primary-light/40 to-primary-light flex items-center justify-center overflow-hidden">
              <FotoProduto src={produto.urlFoto} alt={produto.nomePopular} iconClassName="w-20 h-20 text-primary/40" />
            </div>

            {/* Info */}
            <div className="flex-1 space-y-3">
              <div className="flex items-start justify-between gap-3 flex-wrap">
                <h1 className="text-2xl font-bold text-foreground">{produto.nomePopular}</h1>
                <Badge variant="outline" className={`${statusCfg.className} text-sm`}>
                  {statusCfg.label}
                </Badge>
              </div>

              <div className="flex flex-wrap gap-x-6 gap-y-1 text-sm text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <FlaskConical className="w-3.5 h-3.5" />
                  <em>{produto.nomeCientifico || "—"}</em>
                </span>
                <span className="flex items-center gap-1.5">
                  <BookOpen className="w-3.5 h-3.5" />
                  {produto.familiaBotanica || "—"}
                </span>
              </div>

              {produto.tipo && (
                <span className="inline-block bg-primary-light text-primary-dark text-xs font-medium px-2.5 py-0.5 rounded-full border border-border">
                  {TIPO_PRODUTO_LABEL[produto.tipo]}
                </span>
              )}

              {produto.comunidade && (
                <p className="text-xs text-primary font-medium">
                  🌿 {produto.comunidade}
                  {local && ` · ${local}`}
                </p>
              )}

              <div className="flex gap-4 flex-wrap text-sm">
                {oferta.quantidade != null && (
                  <span className="text-slate-700 font-medium">
                    📦 {oferta.quantidade} {unidade(oferta.tipoPesagem)} em estoque
                  </span>
                )}
                {oferta.preco != null && (
                  <span className="text-primary-dark font-bold text-base">
                    R$ {oferta.preco.toFixed(2)}/{unidade(oferta.tipoPesagem)}
                  </span>
                )}
              </div>

              {descricao && (
                <p className="text-sm text-slate-600 leading-relaxed">{descricao}</p>
              )}
            </div>
          </div>
        </Card>

        {/* ── Order form ── */}
        <Card className="border-border">
          <CardHeader>
            <CardTitle className="text-lg text-foreground">Fazer pedido</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSubmit} className="space-y-4">
              {produto.ofertas.length > 1 && (
                <div className="space-y-1.5">
                  <Label htmlFor="oferta">Oferta</Label>
                  <select
                    id="oferta"
                    value={oferta.estoqueId}
                    onChange={(e) => {
                      setEstoqueId(e.target.value);
                      setTipoProposto(null);
                    }}
                    className="w-full rounded-md border border-border bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                  >
                    {produto.ofertas.map((o) => (
                      <option key={o.estoqueId} value={o.estoqueId}>
                        {DISPONIBILIDADE_CONFIG[o.disponibilidade].label}
                        {o.preco != null && ` · R$ ${o.preco.toFixed(2)}/${unidade(o.tipoPesagem)}`}
                        {o.quantidade != null && ` · ${o.quantidade} ${unidade(o.tipoPesagem)} em estoque`}
                        {o.descricao && ` · ${o.descricao}`}
                      </option>
                    ))}
                  </select>
                </div>
              )}

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                {oferta.aceitaNegociacao ? (
                  <div className="space-y-1.5">
                    <Label htmlFor="tipoPedido">Tipo de negociação</Label>
                    <select
                      id="tipoPedido"
                      value={tipoPedido ?? statusCfg.tipoPedido}
                      onChange={(e) => setTipoProposto(e.target.value as TipoPedido)}
                      className="w-full rounded-md border border-border bg-white px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-ring"
                    >
                      {(Object.keys(TIPO_PEDIDO_LABEL) as TipoPedido[]).map((t) => (
                        <option key={t} value={t}>
                          {TIPO_PEDIDO_LABEL[t]}
                          {t === statusCfg.tipoPedido ? " (oferta)" : " (proposta)"}
                        </option>
                      ))}
                    </select>
                    <p className="text-xs text-slate-400">
                      {tipoPedido === statusCfg.tipoPedido
                        ? "O produtor aceita negociar: você pode propor outro tipo."
                        : `Proposta: a oferta é de ${TIPO_PEDIDO_LABEL[statusCfg.tipoPedido].toLowerCase()}, o produtor vai avaliar.`}
                    </p>
                  </div>
                ) : (
                  <div className="space-y-1.5">
                    <Label>Tipo de negociação</Label>
                    <p className="flex items-center gap-2 rounded-md border border-border bg-primary-light/50 px-3 py-2 text-sm text-slate-700">
                      <Lock className="w-3.5 h-3.5 text-slate-400" />
                      {TIPO_PEDIDO_LABEL[statusCfg.tipoPedido]}
                    </p>
                    <p className="text-xs text-slate-400">
                      O produtor não aceita negociar: só {TIPO_PEDIDO_LABEL[statusCfg.tipoPedido].toLowerCase()}.
                    </p>
                  </div>
                )}
                <div className="space-y-1.5">
                  <Label htmlFor="quantidade">Quantidade ({unidade(oferta.tipoPesagem)})</Label>
                  <Input
                    id="quantidade"
                    type="number"
                    min={0.01}
                    step="any"
                    max={oferta.quantidade ?? undefined}
                    value={form.quantidade}
                    onChange={(e) => setForm((p) => ({ ...p, quantidade: Number(e.target.value) }))}
                    className="border-border focus-visible:ring-ring"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="nome">Seu nome <span className="text-destructive">*</span></Label>
                <Input
                  id="nome"
                  placeholder="Nome completo"
                  maxLength={150}
                  value={form.nome}
                  onChange={(e) => setForm((p) => ({ ...p, nome: e.target.value }))}
                  className="border-border focus-visible:ring-ring"
                  required
                />
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                <div className="space-y-1.5">
                  <Label htmlFor="email">E-mail <span className="text-destructive">*</span></Label>
                  <Input
                    id="email"
                    type="email"
                    placeholder="email@exemplo.com"
                    maxLength={254}
                    value={form.email}
                    onChange={(e) => setForm((p) => ({ ...p, email: e.target.value }))}
                    className="border-border focus-visible:ring-ring"
                    required
                  />
                  <p className="text-xs text-slate-400">Enviamos uma cópia do pedido para este e-mail.</p>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="telefone">Telefone (opcional)</Label>
                  <Input
                    id="telefone"
                    type="tel"
                    placeholder="(11) 99999-9999"
                    maxLength={20}
                    value={form.telefone}
                    onChange={(e) => setForm((p) => ({ ...p, telefone: e.target.value }))}
                    className="border-border focus-visible:ring-ring"
                  />
                </div>
              </div>

              <div className="space-y-1.5">
                <Label htmlFor="mensagemOpcional">Mensagem (opcional)</Label>
                <textarea
                  id="mensagemOpcional"
                  rows={3}
                  maxLength={2000}
                  placeholder="Informações adicionais, forma de entrega, etc."
                  value={form.mensagemOpcional}
                  onChange={(e) => setForm((p) => ({ ...p, mensagemOpcional: e.target.value }))}
                  className="w-full rounded-md border border-border bg-white px-3 py-2 text-sm placeholder:text-slate-400 focus:outline-none focus:ring-2 focus:ring-ring resize-none"
                />
              </div>

              <Button
                type="submit"
                disabled={submitting}
                className="w-full bg-primary hover:bg-primary-dark text-white gap-2"
              >
                {submitting && <Loader2 className="w-4 h-4 animate-spin" />}
                Enviar pedido
              </Button>
            </form>
          </CardContent>
        </Card>
      </main>

      <footer className="bg-foreground text-white/70 text-xs py-5 text-center">
        © {new Date().getFullYear()} Semente Livre. Todos os direitos reservados.
      </footer>
    </div>
  );
}
