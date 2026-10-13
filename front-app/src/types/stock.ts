import { EspecieGeral, TipoProduto, FormatoProduto } from './seed';

export enum Pesagem {
  SACA = 'SACA',
  KG = 'KG',
  GRAMA = 'GRAMA',
  MG = 'MG',
  UNIDADE = 'UNIDADE',
}

export const PesagemLabels: Record<Pesagem, string> = {
  [Pesagem.SACA]: 'Saca',
  [Pesagem.KG]: 'kg',
  [Pesagem.GRAMA]: 'Grama',
  [Pesagem.MG]: 'mg',
  [Pesagem.UNIDADE]: 'Unidade',
};

export enum DisponibilidadeProduto {
  PARA_TROCA = 'PARA_TROCA',
  PARA_VENDA = 'PARA_VENDA',
  PARA_DOACAO = 'PARA_DOACAO',
  A_NEGOCIAR = 'A_NEGOCIAR',
  INDISPONIVEL = 'INDISPONIVEL',
}

export const DisponibilidadeLabels: Record<DisponibilidadeProduto, string> = {
  [DisponibilidadeProduto.PARA_TROCA]: 'Para Troca',
  [DisponibilidadeProduto.PARA_VENDA]: 'Para Venda',
  [DisponibilidadeProduto.PARA_DOACAO]: 'Para Doação',
  [DisponibilidadeProduto.A_NEGOCIAR]: 'A Negociar',
  [DisponibilidadeProduto.INDISPONIVEL]: 'Indisponível',
};

/**
 * Tipo de pedido de cada disponibilidade que vai para o site. Só nelas faz
 * sentido escolher se o comprador pode propor outro tipo (negociar).
 */
export const TipoNegociavelLabels: Partial<Record<DisponibilidadeProduto, string>> = {
  [DisponibilidadeProduto.PARA_VENDA]: 'venda',
  [DisponibilidadeProduto.PARA_TROCA]: 'troca',
  [DisponibilidadeProduto.PARA_DOACAO]: 'doação',
};

export enum TipoMovimentacao {
  ENTRADA = 'ENTRADA',
  SAIDA_VENDA = 'SAIDA_VENDA',
  SAIDA_TROCA = 'SAIDA_TROCA',
  SAIDA_DOACAO = 'SAIDA_DOACAO',
  CORRECAO = 'CORRECAO',
  ZERAMENTO = 'ZERAMENTO',
}

export const TipoMovimentacaoLabels: Record<TipoMovimentacao, string> = {
  [TipoMovimentacao.ENTRADA]: 'Entrada',
  [TipoMovimentacao.SAIDA_VENDA]: 'Saída — Venda',
  [TipoMovimentacao.SAIDA_TROCA]: 'Saída — Troca',
  [TipoMovimentacao.SAIDA_DOACAO]: 'Saída — Doação',
  [TipoMovimentacao.CORRECAO]: 'Correção',
  [TipoMovimentacao.ZERAMENTO]: 'Zeramento',
};

export interface Estoque {
  idEstoque: string;
  idProprietario: string;
  idProduto: string;
  nomePopular: string;
  urlFoto: string;
  descricao?: string;
  preco?: number;
  formaPrecificacao?: string;
  quantidade: number;
  tipoPesagem: Pesagem;
  disponibilidade: DisponibilidadeProduto;
  /** false: no site o comprador só pode pedir o tipo da disponibilidade. Ausente = aceita. */
  aceitaNegociacao?: boolean;
  tipo: TipoMovimentacao;
  dataMovimentacao: Date;
  dataUltimaAtualizacaoEstoque: Date;
  // Campos do /produtos que acompanha este estoque. O formulário de cadastro já
  // os coletava, mas createSeed os descartava porque o tipo não os carregava —
  // e o backend exige `especie` e `formato`.
  // `tipo` acima é o TipoMovimentacao; o tipo do produto vem em `tipoProduto`.
  tipoProduto?: TipoProduto;
  especie?: EspecieGeral;
  formato?: FormatoProduto;
  nomeCientifico?: string;
  historico?: string;
}

export interface MovimentacaoEstoque {
  id: string;
  idProduto: string;
  idProprietario: string;
  tipo: TipoMovimentacao;
  quantidade: number;
  delta: number;
  motivo?: string;
  dataMovimentacao: Date;
}

/**
 * De onde veio a movimentação. O backend grava isso para o relatório de
 * auditoria separar o que o produtor fez na mão do que o sistema gerou.
 */
export enum OrigemMovimentacao {
  CADASTRO = 'CADASTRO',
  AJUSTE_MANUAL = 'AJUSTE_MANUAL',
  PEDIDO = 'PEDIDO',
  DEVOLUCAO = 'DEVOLUCAO',
  EXCLUSAO_PRODUTO = 'EXCLUSAO_PRODUTO',
}

export const OrigemMovimentacaoLabels: Record<OrigemMovimentacao, string> = {
  [OrigemMovimentacao.CADASTRO]: 'Cadastro do estoque',
  [OrigemMovimentacao.AJUSTE_MANUAL]: 'Ajuste manual',
  [OrigemMovimentacao.PEDIDO]: 'Pedido',
  [OrigemMovimentacao.DEVOLUCAO]: 'Devolução',
  [OrigemMovimentacao.EXCLUSAO_PRODUTO]: 'Exclusão do produto',
};
