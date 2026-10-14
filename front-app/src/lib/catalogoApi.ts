/**
 * catalogoApi.ts — Produtos e estoques do proprietário.
 *
 * No front o item de tela é uma entidade única (`Estoque`), mas o backend separa
 * em dois recursos: `/produtos` (o que é a semente) e `/estoques` (quanto há,
 * preço e disponibilidade). O join das duas fica em hooks/useSeeds.ts.
 *
 * Endpoints consumidos:
 *  GET    /produtos              → List<ProdutoDTO>
 *  POST   /produtos              → ProdutoDTO
 *  PUT    /produtos/{id}         → ProdutoDTO
 *  DELETE /produtos/{id}         → 204
 *  POST   /produtos/upload-foto  → string (caminho relativo, ex.: "/uploads/produtos/x.png")
 *  GET    /estoques              → List<EstoqueDTO>
 *  POST   /estoques              → EstoqueDTO
 *  PUT    /estoques/{id}         → EstoqueDTO
 *  DELETE /estoques/{id}         → 204
 *
 * Observação: `listarTodos()` dos dois recursos não recebe filtro de dono;
 * quem restringe por `proprietarioId` é o hook.
 */

import { apiDelete, apiGet, apiPost, apiPut, apiUpload } from './api';

// ── Enums (espelham com.sementelivre.backend.entity.enums) ──────────────────────

export type TipoProdutoDTO = 'HORTALICA' | 'FRUTIFERA' | 'FORRAGEIRA' | 'CEREAL' | 'LEGUMINOSA' | 'VERDURA' | 'MEDICINAL' | 'OUTRAS';
export type EspecieGeralDTO = 'FEIJAO' | 'MILHO' | 'ABOBORA' | 'ALFACE' | 'ARROZ' | 'CEBOLA' | 'ALHO' | 'OUTRAS';
export type FormatoProdutoDTO = 'MUDA' | 'SEMENTE';
export type PesagemDTO = 'SACA' | 'KG' | 'GRAMA' | 'MG' | 'UNIDADE';
export type DisponibilidadeDTO = 'PARA_TROCA' | 'PARA_VENDA' | 'PARA_DOACAO' | 'A_NEGOCIAR' | 'INDISPONIVEL';
export type TipoMovimentacaoDTO = 'ENTRADA' | 'SAIDA_VENDA' | 'SAIDA_TROCA' | 'SAIDA_DOACAO' | 'CORRECAO' | 'ZERAMENTO';

// ── DTOs ───────────────────────────────────────────────────────────────────────

export interface ProdutoDTO {
  id: string;
  nomePopular: string;
  nomeCientifico: string | null;
  historico: string | null;
  urlFoto: string;
  tipo: TipoProdutoDTO;
  especie: EspecieGeralDTO;
  formato: FormatoProdutoDTO;
  familiaBotanica: string | null;
  comunidadeOrigemId: string | null;
  dataInclusao: string;
  dataUltimaAlteracao: string;
}

export interface ProdutoRequestDTO {
  nomePopular: string;
  nomeCientifico?: string | null;
  historico?: string | null;
  urlFoto: string;
  tipo: TipoProdutoDTO;
  especie: EspecieGeralDTO;
  formato: FormatoProdutoDTO;
  familiaBotanica?: string | null;
  comunidadeOrigemId?: string | null;
}

export interface EstoqueDTO {
  id: string;
  proprietarioId: string;
  produtoId: string;
  descricao: string | null;
  preco: number | null;
  quantidade: number;
  tipoPesagem: PesagemDTO;
  disponibilidade: DisponibilidadeDTO;
  tipoMovimentacao: TipoMovimentacaoDTO;
  dataMovimentacao: string;
  dataUltimaAtualizacao: string;
  /** false: no site o pedido fica preso ao tipo da disponibilidade. */
  aceitaNegociacao: boolean;
}

export interface EstoqueRequestDTO {
  proprietarioId: string;
  produtoId: string;
  descricao?: string | null;
  preco: number;
  quantidade: number;
  tipoPesagem: PesagemDTO;
  disponibilidade: DisponibilidadeDTO;
  tipoMovimentacao: TipoMovimentacaoDTO;
  aceitaNegociacao?: boolean;
}

// ── Produtos ───────────────────────────────────────────────────────────────────

export function listarProdutos(): Promise<ProdutoDTO[]> {
  return apiGet<ProdutoDTO[]>('/produtos');
}

export function buscarProduto(id: string): Promise<ProdutoDTO> {
  return apiGet<ProdutoDTO>(`/produtos/${id}`);
}

export function criarProduto(dados: ProdutoRequestDTO): Promise<ProdutoDTO> {
  return apiPost<ProdutoDTO>('/produtos', dados);
}

export function atualizarProduto(id: string, dados: ProdutoRequestDTO): Promise<ProdutoDTO> {
  return apiPut<ProdutoDTO>(`/produtos/${id}`, dados);
}

export function excluirProduto(id: string): Promise<void> {
  return apiDelete<void>(`/produtos/${id}`);
}

/** Envia a imagem e devolve o caminho relativo guardado pelo backend. */
export function enviarFotoProduto(arquivo: File): Promise<string> {
  const form = new FormData();
  form.append('foto', arquivo);
  return apiUpload<string>('/produtos/upload-foto', form);
}

// ── Estoques ───────────────────────────────────────────────────────────────────

export function listarEstoques(): Promise<EstoqueDTO[]> {
  return apiGet<EstoqueDTO[]>('/estoques');
}

export function criarEstoque(dados: EstoqueRequestDTO): Promise<EstoqueDTO> {
  return apiPost<EstoqueDTO>('/estoques', dados);
}

export function atualizarEstoque(id: string, dados: EstoqueRequestDTO): Promise<EstoqueDTO> {
  return apiPut<EstoqueDTO>(`/estoques/${id}`, dados);
}

export function excluirEstoque(id: string): Promise<void> {
  return apiDelete<void>(`/estoques/${id}`);
}
