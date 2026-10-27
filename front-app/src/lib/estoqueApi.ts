import { apiGet, apiPost } from './api';
import { OrigemMovimentacao, TipoMovimentacao } from '@/types/stock';

/**
 * Linha do histórico de estoque.
 *
 * Espelha MovimentacaoResponseDTO do backend. `aumento` vem pronto do servidor
 * porque a UI precisa pintar a linha de ▲/▼, e decidir isso no cliente exigiria
 * comparar saldoPosterior com saldoAnterior em todo lugar.
 */
export interface Movimentacao {
  id: string;
  estoqueId: string;
  tipo: TipoMovimentacao;
  origem: OrigemMovimentacao;
  quantidade: number;
  saldoAnterior: number;
  saldoPosterior: number;
  aumento: boolean;
  descricao?: string;
  usuarioNome?: string;
  dataMovimentacao: string;
}

/** Envelope de página, no mesmo formato que o Spring Data devolve. */
export interface Pagina<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface NovaMovimentacao {
  tipo: TipoMovimentacao;
  quantidade: number;
  descricao?: string;
}

/** Histórico paginado de um estoque, do mais recente para o mais antigo. */
export function buscarMovimentacoesApi(
  estoqueId: string,
  page = 0,
  size = 20
): Promise<Pagina<Movimentacao>> {
  return apiGet<Pagina<Movimentacao>>(`/estoques/${estoqueId}/movimentacoes?page=${page}&size=${size}`);
}

/** Lança entrada ou saída e ajusta o saldo do estoque na mesma transação. */
export function registrarMovimentacaoApi(
  estoqueId: string,
  dados: NovaMovimentacao
): Promise<Movimentacao> {
  return apiPost<Movimentacao>(`/estoques/${estoqueId}/movimentacoes`, dados);
}
