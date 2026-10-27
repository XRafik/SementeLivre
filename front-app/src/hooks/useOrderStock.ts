'use client';

import { useCallback, useEffect, useState } from 'react';
import { apiGet, errorMessage } from '@/lib/api';
import { DisponibilidadeProduto, Pesagem } from '@/types/stock';
import { useAuth } from '@/context/AuthContext';

/** Semente em estoque que pode entrar em um pedido. */
export interface EstoqueParaPedido {
  idEstoque: string;
  idProduto: string;
  nomePopular: string;
  quantidade: number;
  tipoPesagem: Pesagem;
  preco?: number;
  disponibilidade: DisponibilidadeProduto;
}

interface EstoqueApi {
  id: string;
  proprietarioId: string;
  produtoId: string;
  preco: number | null;
  quantidade: number;
  tipoPesagem: Pesagem;
  disponibilidade: DisponibilidadeProduto;
}

interface ProdutoApi {
  id: string;
  nomePopular: string;
}

/**
 * Estoque do proprietário logado, lido do backend. O formulário de pedido usa
 * os ids reais de produto (UUID) daqui, e a quantidade mostrada é o saldo
 * atual, já descontadas as reservas de pedidos PENDENTE/CONFIRMADO.
 */
export function useOrderStock() {
  // O login so aceita conta de proprietario; o id da sessao e o do proprietario
  const { user } = useAuth();
  const proprietarioId = user?.uid;

  const [version, setVersion] = useState(0);
  const requestKey = JSON.stringify([proprietarioId, version]);
  const [result, setResult] = useState<{ key: string; stock: EstoqueParaPedido[]; error: string | null }>();

  useEffect(() => {
    if (!proprietarioId) return;
    let ignore = false;
    // /estoques ainda nao filtra por proprietario no backend
    Promise.all([
      apiGet<EstoqueApi[]>('/estoques'),
      apiGet<ProdutoApi[]>('/produtos'),
    ])
      .then(([estoques, produtos]) => {
        if (ignore) return;
        const nomes = new Map(produtos.map((p) => [p.id, p.nomePopular]));
        const stock = estoques
          .filter((e) => e.proprietarioId === proprietarioId)
          .map((e) => ({
            idEstoque: e.id,
            idProduto: e.produtoId,
            nomePopular: nomes.get(e.produtoId) ?? 'Produto',
            quantidade: e.quantidade,
            tipoPesagem: e.tipoPesagem,
            preco: e.preco ?? undefined,
            disponibilidade: e.disponibilidade,
          }))
          .sort((a, b) => a.nomePopular.localeCompare(b.nomePopular, 'pt-BR'));
        setResult({ key: requestKey, stock, error: null });
      })
      .catch((err) => {
        if (!ignore) {
          setResult((prev) => ({
            key: requestKey,
            stock: prev?.stock ?? [],
            error: errorMessage(err, 'Erro ao carregar estoque.'),
          }));
        }
      });
    return () => { ignore = true; };
  }, [requestKey, proprietarioId]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);

  const stock = proprietarioId ? result?.stock ?? [] : [];
  const loading = !!proprietarioId && result?.key !== requestKey;
  const error = proprietarioId ? result?.error ?? null : null;

  return { stock, loading, error, reload };
}
