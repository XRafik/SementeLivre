'use client';

import { useEffect, useState, useCallback } from 'react';
import { apiUrl, isApiError } from '@/lib/api';
import {
  atualizarEstoque,
  atualizarProduto,
  buscarProduto,
  criarEstoque,
  criarProduto,
  enviarFotoProduto,
  excluirEstoque,
  excluirProduto,
  listarEstoques,
  listarProdutos,
  type EstoqueDTO,
  type ProdutoDTO,
} from '@/lib/catalogoApi';
import { mockSementes } from '@/data/mockSementes';
import { Estoque, DisponibilidadeProduto, Pesagem, TipoMovimentacao } from '@/types/stock';
import { EspecieGeral, FormatoProduto, TipoProduto } from '@/types/seed';
import { useAuth } from '@/context/AuthContext';

// ── Join /produtos + /estoques no item de tela ────────────────────────────────

function tipoDoAjuste(saldoAtual: number, novo: number | undefined, tipoAtual: TipoMovimentacao): TipoMovimentacao {
  if (novo === undefined || novo === saldoAtual) return tipoAtual;
  if (novo > saldoAtual) return TipoMovimentacao.ENTRADA;
  return novo === 0 ? TipoMovimentacao.ZERAMENTO : TipoMovimentacao.CORRECAO;
}

function paraData(iso: string | null | undefined): Date {
  return iso ? new Date(iso) : new Date();
}

function juntar(produto: ProdutoDTO, estoque: EstoqueDTO): Estoque {
  return {
    idEstoque: estoque.id,
    idProprietario: estoque.proprietarioId,
    idProduto: estoque.produtoId,
    nomePopular: produto.nomePopular,
    urlFoto: apiUrl(produto.urlFoto),
    descricao: estoque.descricao ?? undefined,
    preco: estoque.preco ?? undefined,
    quantidade: estoque.quantidade,
    // O DTO espelha o enum do backend como union de strings; aqui convertemos
    // para os enums que as telas já usam.
    tipoPesagem: estoque.tipoPesagem as Pesagem,
    disponibilidade: estoque.disponibilidade as DisponibilidadeProduto,
    aceitaNegociacao: estoque.aceitaNegociacao,
    tipo: estoque.tipoMovimentacao as TipoMovimentacao,
    dataMovimentacao: paraData(estoque.dataMovimentacao),
    dataUltimaAtualizacaoEstoque: paraData(estoque.dataUltimaAtualizacao),
    tipoProduto: produto.tipo as TipoProduto,
    especie: produto.especie as EspecieGeral,
    formato: produto.formato as FormatoProduto,
    nomeCientifico: produto.nomeCientifico ?? undefined,
    historico: produto.historico ?? undefined,
  };
}

function mensagemDeErro(err: unknown, padrao: string): string {
  if (isApiError(err)) {
    const primeiroCampo = Object.values(err.fieldErrors)[0];
    return primeiroCampo ?? err.message ?? padrao;
  }
  return padrao;
}

export function useSeeds() {
  const { user } = useAuth();
  const [seeds, setSeeds] = useState<Estoque[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const recarregar = useCallback(async () => {
    if (!user) {
      setSeeds([...mockSementes]);
      setLoading(false);
      return;
    }
    setLoading(true);
    try {
      const [produtos, estoques] = await Promise.all([listarProdutos(), listarEstoques()]);
      const porId = new Map(produtos.map((p) => [p.id, p]));
      const meus = estoques
        .filter((e) => e.proprietarioId === user.uid)
        .map((e) => {
          const produto = porId.get(e.produtoId);
          return produto ? juntar(produto, e) : null;
        })
        .filter((s): s is Estoque => s !== null)
        .sort((a, b) => b.dataMovimentacao.getTime() - a.dataMovimentacao.getTime());

      setSeeds(meus);
      setError(null);
    } catch (err) {
      setError(mensagemDeErro(err, 'Não foi possível carregar seus produtos.'));
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    let cancelado = false;
    void (async () => {
      await recarregar();
      if (cancelado) return;
    })();
    return () => {
      cancelado = true;
    };
  }, [recarregar]);

  const createSeed = useCallback(
    async (data: Omit<Estoque, 'idEstoque'>): Promise<string> => {
      if (!user) throw new Error('Sessão expirada. Faça login novamente.');

      // O backend separa o "o que é" (/produtos) do "quanto há" (/estoques),
      // então o cadastro é em duas etapas e devolvemos o id do estoque — que é
      // o que as telas usam em /sementes/[id].
      const produto = await criarProduto({
        nomePopular: data.nomePopular,
        nomeCientifico: data.nomeCientifico ?? null,
        historico: data.historico ?? null,
        urlFoto: data.urlFoto,
        tipo: data.tipoProduto!,
        especie: data.especie!,
        formato: data.formato!,
      });

      const estoque = await criarEstoque({
        proprietarioId: user.uid,
        produtoId: produto.id,
        descricao: data.descricao ?? null,
        preco: data.preco ?? 0,
        quantidade: data.quantidade,
        tipoPesagem: data.tipoPesagem,
        disponibilidade: data.disponibilidade,
        tipoMovimentacao: data.tipo,
        aceitaNegociacao: data.aceitaNegociacao ?? true,
      });

      await recarregar();
      return estoque.id;
    },
    [user, recarregar]
  );

  const updateSeed = useCallback(
    async (id: string, data: Partial<Estoque>) => {
      const atual = seeds.find((s) => s.idEstoque === id);
      if (!atual) throw new Error('Produto não encontrado.');

      const {
        nomePopular, urlFoto, nomeCientifico, historico, tipoProduto, especie, formato,
        descricao, preco, quantidade, tipoPesagem, disponibilidade, aceitaNegociacao,
      } = data;

      const mexeuNoProduto =
        nomePopular !== undefined || urlFoto !== undefined || nomeCientifico !== undefined ||
        historico !== undefined || tipoProduto !== undefined || especie !== undefined || formato !== undefined;

      const mexeuNoEstoque =
        descricao !== undefined || preco !== undefined || quantidade !== undefined ||
        tipoPesagem !== undefined || disponibilidade !== undefined || aceitaNegociacao !== undefined;

      if (atual.idProprietario !== user?.uid) {
        throw new Error('Você só pode editar os seus próprios produtos.');
      }

      if (mexeuNoProduto) {
        // /produtos exige o registro completo: parte do que não mudou vem do estado atual.
        await atualizarProduto(atual.idProduto, {
          nomePopular: nomePopular ?? atual.nomePopular,
          nomeCientifico: nomeCientifico ?? atual.nomeCientifico ?? null,
          historico: historico ?? atual.historico ?? null,
          urlFoto: urlFoto ?? atual.urlFoto,
          tipo: tipoProduto ?? atual.tipoProduto!,
          especie: especie ?? atual.especie!,
          formato: formato ?? atual.formato!,
        });
      }

      if (mexeuNoEstoque) {
        await atualizarEstoque(id, {
          proprietarioId: atual.idProprietario,
          produtoId: atual.idProduto,
          descricao: descricao ?? atual.descricao ?? null,
          preco: preco ?? atual.preco ?? 0,
          quantidade: quantidade ?? atual.quantidade,
          tipoPesagem: tipoPesagem ?? atual.tipoPesagem,
          disponibilidade: disponibilidade ?? atual.disponibilidade,
          // Ajuste de quantidade é entrada ou correção; reenviar o tipo da última
          // movimentação gravava, por exemplo, "saída por venda" num ajuste manual
          tipoMovimentacao: tipoDoAjuste(atual.quantidade, quantidade, atual.tipo),
          aceitaNegociacao: aceitaNegociacao ?? atual.aceitaNegociacao,
        });
      }

      await recarregar();
    },
    [seeds, user, recarregar]
  );

  const deleteSeed = useCallback(
    async (id: string) => {
      const atual = seeds.find((s) => s.idEstoque === id);
      if (!atual) throw new Error('Produto não encontrado.');
      if (atual.idProprietario !== user?.uid) {
        throw new Error('Você só pode excluir os seus próprios produtos.');
      }

      await excluirEstoque(id);
      // O produto pode estar referenciado por itens de pedido já feitos; nesse caso
      // o backend recusa a remoção e mantemos o registro para preservar o histórico.
      await excluirProduto(atual.idProduto).catch(() => undefined);

      await recarregar();
    },
    [seeds, user, recarregar]
  );

  const uploadSeedPhoto = useCallback(async (file: File, _seedId: string): Promise<string> => {
    // Devolve o caminho relativo: é o que gravamos no /produtos, para o banco não
    // ficar preso a localhost:8080. A leitura resolve via apiUrl().
    return enviarFotoProduto(file);
  }, []);

  const getSeed = useCallback(
    async (id: string): Promise<Estoque | null> => {
      if (!user) return mockSementes.find((m) => m.idEstoque === id) ?? null;
      try {
        const estoques = await listarEstoques();
        const estoque = estoques.find((e) => e.id === id);
        if (!estoque) return null;
        const produto = await buscarProduto(estoque.produtoId);
        return juntar(produto, estoque);
      } catch {
        return seeds.find((s) => s.idEstoque === id) ?? null;
      }
    },
    [user, seeds]
  );

  return { seeds, loading, error, createSeed, updateSeed, deleteSeed, uploadSeedPhoto, getSeed, recarregar };
}
