'use client';

import { useCallback, useEffect, useState } from 'react';
import { apiGet, apiPatch, apiPost, comQuery, errorMessage, isApiError } from '@/lib/api';
import { FiltroPedidos, NovoPedido, Pedido, StatusPedido, TipoPedido } from '@/types/order';
import { useAuth } from '@/context/AuthContext';

// Formato de PedidoResponseDTO no backend
interface PedidoApi {
  id: string;
  tipoPedido: TipoPedido;
  mensagemOpcional: string | null;
  dataPedido: string;
  status: StatusPedido;
  usuarioSolicitanteId: string;
  proprietarioRecebedorId: string;
  itens: {
    id: string;
    produtoId: string;
    nomeProduto: string | null;
    quantidade: number;
    precoUnitario: number | null;
  }[];
  comprador: { nome: string; telefone: string | null; email?: string | null } | null;
}

function toPedido(api: PedidoApi): Pedido {
  const itens = api.itens.map((item) => ({
    idItem: item.id,
    idProduto: item.produtoId,
    nomePopular: item.nomeProduto ?? 'Produto',
    quantidade: item.quantidade,
    precoUnitario: item.precoUnitario ?? undefined,
  }));

  const totalValor = api.tipoPedido === TipoPedido.VENDA
    ? itens.reduce((sum, i) => sum + (i.precoUnitario ?? 0) * i.quantidade, 0)
    : undefined;

  return {
    idPedido: api.id,
    idProprietario: api.proprietarioRecebedorId,
    tipoPedido: api.tipoPedido,
    status: api.status,
    nomeRecebedor: api.comprador?.nome ?? 'Comprador não informado',
    // Pedido do site pode ter só e-mail, só telefone ou os dois
    contatoRecebedor: [api.comprador?.telefone, api.comprador?.email].filter(Boolean).join(' · ') || undefined,
    mensagemOpcional: api.mensagemOpcional ?? undefined,
    // LocalDateTime chega sem fuso; o navegador interpreta como horário local
    dataPedido: new Date(api.dataPedido),
    itens,
    totalValor,
  };
}

export function useOrders(filtro?: FiltroPedidos) {
  // O login so aceita conta de proprietario; o id da sessao e o do proprietario
  const { user } = useAuth();
  const proprietarioId = user?.uid;

  const { dataInicio, dataFim, tipoPedido, produtoId, status } = filtro ?? {};
  const [version, setVersion] = useState(0);

  // Cada combinacao de proprietario + filtros + versao identifica uma consulta;
  // "loading" e derivado de a ultima resposta ser de outra consulta.
  const requestKey = JSON.stringify([proprietarioId, dataInicio, dataFim, tipoPedido, produtoId, status, version]);
  const [result, setResult] = useState<{ key: string; orders: Pedido[]; error: string | null }>();

  useEffect(() => {
    if (!proprietarioId) return;
    let ignore = false;
    apiGet<PedidoApi[]>(
      comQuery('/pedidos', { proprietarioId, dataInicio, dataFim, tipoPedido, produtoId, status })
    )
      .then((data) => {
        if (!ignore) setResult({ key: requestKey, orders: data.map(toPedido), error: null });
      })
      .catch((err) => {
        if (!ignore) {
          setResult((prev) => ({
            key: requestKey,
            orders: prev?.orders ?? [],
            error: errorMessage(err, 'Erro ao carregar pedidos.'),
          }));
        }
      });
    // Descarta resposta de filtro antigo que chegue depois da atual
    return () => { ignore = true; };
  }, [requestKey, proprietarioId, dataInicio, dataFim, tipoPedido, produtoId, status]);

  const reload = useCallback(() => setVersion((v) => v + 1), []);

  const orders = proprietarioId ? result?.orders ?? [] : [];
  const loading = !!proprietarioId && result?.key !== requestKey;
  const error = proprietarioId ? result?.error ?? null : 'Perfil de proprietário não encontrado.';

  /** Registra o pedido na fila; o backend confere o saldo, sem baixar. Retorna o id. */
  const createOrder = useCallback(
    async (data: NovoPedido): Promise<string> => {
      if (!proprietarioId) throw new Error('Perfil de proprietário não encontrado.');
      const criado = await apiPost<PedidoApi>('/pedidos', {
          tipoPedido: data.tipoPedido,
          mensagemOpcional: data.mensagemOpcional || null,
          proprietarioRecebedorId: proprietarioId,
          comprador: {
            nome: data.nomeRecebedor.trim(),
            telefone: data.contatoRecebedor?.trim() || null,
          },
          itens: data.itens.map((i) => ({
            produtoId: i.idProduto,
            quantidade: i.quantidade,
            precoUnitario: i.precoUnitario ?? null,
          })),
      });
      return criado.id;
    },
    [proprietarioId]
  );

  const confirmOrder = useCallback(async (id: string): Promise<Pedido> => {
    return toPedido(await apiPatch<PedidoApi>(`/pedidos/${id}/confirmar`));
  }, []);

  /** Cancela; o backend devolve o estoque se o pedido já estava confirmado. */
  const cancelOrder = useCallback(async (id: string): Promise<Pedido> => {
    return toPedido(await apiPatch<PedidoApi>(`/pedidos/${id}/cancelar`));
  }, []);

  const getOrder = useCallback(async (id: string): Promise<Pedido | null> => {
    try {
      return toPedido(await apiGet<PedidoApi>(`/pedidos/${id}`));
    } catch (err) {
      if (isApiError(err) && err.status === 404) return null;
      throw err;
    }
  }, []);

  return { orders, loading, error, reload, createOrder, cancelOrder, confirmOrder, getOrder };
}
