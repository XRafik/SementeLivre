/**
 * pedidoApi.ts — Pedidos e notificações do proprietário.
 *
 * Endpoints consumidos:
 *  GET    /pedidos?proprietarioId=UUID → List<PedidoDTO>
 *  POST   /pedidos                    → PedidoDTO
 *  GET    /pedidos/{id}               → PedidoDTO
 *  PUT    /pedidos/{id}               → PedidoDTO
 *  DELETE /pedidos/{id}               → 204
 *  PATCH  /pedidos/{id}/confirmar     → PedidoDTO
 *  PATCH  /pedidos/{id}/cancelar      → PedidoDTO
 *
 *  GET    /notificacoes               → List<NotificacaoDTO>
 *  POST   /notificacoes               → NotificacaoDTO
 *  PATCH  /notificacoes/{id}/lida     → NotificacaoDTO (sem corpo)
 *  DELETE /notificacoes/{id}          → 204
 *
 * Observação: `GET /notificacoes` e `GET /pedidos` sem filtro devolvem tudo;
 * o hook filtra pelo `proprietarioId` do dono logado.
 */

import { apiDelete, apiGet, apiPatch, apiPost, apiPut } from './api';

export type TipoPedidoDTO = 'VENDA' | 'TROCA' | 'DOACAO';
export type StatusPedidoDTO = 'PENDENTE' | 'CONFIRMADO' | 'CANCELADO';

// ── DTOs ───────────────────────────────────────────────────────────────────────

export interface ItemPedidoDTO {
  id: string;
  produtoId: string;
  quantidade: number;
  precoUnitario: number | null;
}

export interface ItemPedidoRequestDTO {
  produtoId: string;
  quantidade: number;
  precoUnitario: number;
}

export interface PedidoDTO {
  id: string;
  tipoPedido: TipoPedidoDTO;
  mensagemOpcional: string | null;
  dataPedido: string;
  status: StatusPedidoDTO;
  usuarioSolicitanteId: string;
  proprietarioRecebedorId: string;
  itens: ItemPedidoDTO[];
}

export interface PedidoRequestDTO {
  tipoPedido: TipoPedidoDTO;
  mensagemOpcional?: string | null;
  usuarioSolicitanteId: string;
  proprietarioRecebedorId: string;
  itens: ItemPedidoRequestDTO[];
}

export interface PedidoUpdateDTO {
  tipoPedido: TipoPedidoDTO;
  mensagemOpcional?: string | null;
  itens: ItemPedidoRequestDTO[];
}

export interface NotificacaoDTO {
  id: string;
  titulo: string;
  mensagem: string;
  lida: boolean;
  dataGeracao: string;
  dataLeitura: string | null;
  proprietarioId: string;
  pedidoRelacionadoId: string | null;
}

export interface NotificacaoRequestDTO {
  titulo: string;
  mensagem: string;
  proprietarioId: string;
  pedidoRelacionadoId?: string | null;
}

// ── Pedidos ────────────────────────────────────────────────────────────────────

export function listarPedidos(proprietarioId: string): Promise<PedidoDTO[]> {
  return apiGet<PedidoDTO[]>(`/pedidos?proprietarioId=${encodeURIComponent(proprietarioId)}`);
}

export function buscarPedido(id: string): Promise<PedidoDTO> {
  return apiGet<PedidoDTO>(`/pedidos/${id}`);
}

export function criarPedido(dados: PedidoRequestDTO): Promise<PedidoDTO> {
  return apiPost<PedidoDTO>('/pedidos', dados);
}

export function atualizarPedido(id: string, dados: PedidoUpdateDTO): Promise<PedidoDTO> {
  return apiPut<PedidoDTO>(`/pedidos/${id}`, dados);
}

export function confirmarPedido(id: string): Promise<PedidoDTO> {
  return apiPatch<PedidoDTO>(`/pedidos/${id}/confirmar`);
}

export function cancelarPedido(id: string): Promise<PedidoDTO> {
  return apiPatch<PedidoDTO>(`/pedidos/${id}/cancelar`);
}

export function excluirPedido(id: string): Promise<void> {
  return apiDelete<void>(`/pedidos/${id}`);
}

// ── Notificações ───────────────────────────────────────────────────────────────

export function listarNotificacoes(): Promise<NotificacaoDTO[]> {
  return apiGet<NotificacaoDTO[]>('/notificacoes');
}

export function criarNotificacao(dados: NotificacaoRequestDTO): Promise<NotificacaoDTO> {
  return apiPost<NotificacaoDTO>('/notificacoes', dados);
}

export function marcarNotificacaoLida(id: string): Promise<NotificacaoDTO> {
  return apiPatch<NotificacaoDTO>(`/notificacoes/${id}/lida`);
}

export function excluirNotificacao(id: string): Promise<void> {
  return apiDelete<void>(`/notificacoes/${id}`);
}
