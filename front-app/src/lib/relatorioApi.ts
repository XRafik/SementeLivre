/**
 * relatorioApi.ts — Registro de relatórios gerados.
 *
 * Endpoints consumidos:
 *  POST /relatorios → RelatorioDTO (201)
 *  GET  /relatorios → List<RelatorioDTO>
 *  GET  /relatorios/{id} → RelatorioDTO
 *  PUT  /relatorios/{id} → RelatorioDTO
 *  DELETE /relatorios/{id} → 204
 *
 * O backend guarda o histórico de qual relatório foi emitido, com que filtros e
 * por qual proprietário. A prévia e o CSV continuam sendo montados no navegador
 * a partir de useSeeds/useOrders — aqui só registramos a emissão.
 *
 * `GET /relatorios` não aceita filtro de dono; quem restringe é o hook.
 */

import { apiDelete, apiGet, apiPost, apiPut } from './api';

export type TipoRelatorioDTO = 'ESTOQUE_SEMENTES' | 'PEDIDOS_REALIZADOS';

export interface RelatorioDTO {
  id: string;
  tipo: TipoRelatorioDTO;
  filtrosUtilizados: Record<string, unknown> | null;
  dataGeracao: string;
  proprietarioId: string;
}

export interface RelatorioRequestDTO {
  tipo: TipoRelatorioDTO;
  filtrosUtilizados: Record<string, unknown>;
  proprietarioId: string;
}

export function listarRelatorios(): Promise<RelatorioDTO[]> {
  return apiGet<RelatorioDTO[]>('/relatorios');
}

export function buscarRelatorio(id: string): Promise<RelatorioDTO> {
  return apiGet<RelatorioDTO>(`/relatorios/${id}`);
}

export function criarRelatorio(dados: RelatorioRequestDTO): Promise<RelatorioDTO> {
  return apiPost<RelatorioDTO>('/relatorios', dados);
}

export function atualizarRelatorio(id: string, dados: RelatorioRequestDTO): Promise<RelatorioDTO> {
  return apiPut<RelatorioDTO>(`/relatorios/${id}`, dados);
}

export function excluirRelatorio(id: string): Promise<void> {
  return apiDelete<void>(`/relatorios/${id}`);
}
