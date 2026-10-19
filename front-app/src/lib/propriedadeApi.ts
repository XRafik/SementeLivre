/**
 * propriedadeApi.ts — Propriedades e comunidades do proprietário.
 *
 * Endpoints consumidos:
 *  GET    /api/propriedades          → List<PropriedadeResponseDTO> (só as do dono logado)
 *  POST   /api/propriedades          → PropriedadeResponseDTO
 *  PUT    /api/propriedades/{id}     → PropriedadeResponseDTO
 *  DELETE /api/propriedades/{id}     → 204
 *  GET    /api/comunidades           → List<ComunidadeResponseDTO> (todas; a tela filtra)
 *  POST   /api/comunidades           → ComunidadeResponseDTO (nasce PENDENTE_APROVACAO)
 *
 * O endereço vai junto no corpo (`endereco`): o backend cria o logradouro.
 */

import { apiDelete, apiGet, apiPost, apiPut } from './api';
import { StatusComunidade } from '@/types/property';

export interface EnderecoDTO {
  logradouro: string;
  numero?: string;
  complemento?: string;
  bairro?: string;
  municipio: string;
  uf: string;
  cep?: string;
}

export interface PropriedadeDTO {
  id: string;
  nome: string;
  tamanhoHectares: number;
  dataCadastro: string;
  dataUltimaAlteracao: string;
  proprietarioId: string;
  proprietarioNome: string;
  comunidadeId: string;
  comunidadeNome: string;
  logradouroUf: string | null;
  logradouroMunicipio: string | null;
  endereco: EnderecoDTO | null;
}

export interface PropriedadeRequestDTO {
  nome: string;
  tamanhoHectares: number;
  proprietarioId: string;
  comunidadeId: string;
  endereco: EnderecoDTO;
}

export interface ComunidadeDTO {
  id: string;
  nome: string;
  status: StatusComunidade;
  dataSolicitacao: string;
  dataAprovacao: string | null;
  logradouroUf: string | null;
  logradouroMunicipio: string | null;
}

export interface ComunidadeRequestDTO {
  nome: string;
  endereco: EnderecoDTO;
}

export function listarPropriedades(): Promise<PropriedadeDTO[]> {
  return apiGet<PropriedadeDTO[]>('/api/propriedades');
}

export function criarPropriedade(dados: PropriedadeRequestDTO): Promise<PropriedadeDTO> {
  return apiPost<PropriedadeDTO>('/api/propriedades', dados);
}

export function atualizarPropriedade(id: string, dados: PropriedadeRequestDTO): Promise<PropriedadeDTO> {
  return apiPut<PropriedadeDTO>(`/api/propriedades/${id}`, dados);
}

export function excluirPropriedade(id: string): Promise<void> {
  return apiDelete<void>(`/api/propriedades/${id}`);
}

export function listarComunidades(): Promise<ComunidadeDTO[]> {
  return apiGet<ComunidadeDTO[]>('/api/comunidades');
}

export function solicitarComunidade(dados: ComunidadeRequestDTO): Promise<ComunidadeDTO> {
  return apiPost<ComunidadeDTO>('/api/comunidades', dados);
}
