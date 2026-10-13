export type SpeciesStatus = "exchange" | "sale" | "donation" | "unavailable";

export interface Species {
  id_especie: string;
  nome_popular: string;
  nome_cientifico: string;
  familia_botanica: string;
  descricao: string;
  foto: string;
  status: SpeciesStatus;
  id_comunidade?: string;
  tipoSemente?: TipoSemente;
  quantidadeEstoque?: number;
  preco?: number;
  unidadePesagem?: UnidadePesagem;
}

export interface Comunidade {
  id_comunidade: string;
  nome: string;
  localizacao: string;
  status: "ativa" | "inativa";
}

export type TipoSemente = "HORTALICA" | "FRUTIFERA" | "FORRAGEIRA" | "CEREAL" | "LEGUMINOSA" | "OUTRAS";
export type UnidadePesagem = "SACA" | "KG" | "GRAMA" | "MG" | "UNIDADE";
export type TipoPedido = "VENDA" | "TROCA" | "DOACAO";
export type StatusPedido = "PENDENTE" | "CONFIRMADO" | "CANCELADO";

export interface Pedido {
  id_pedido: string;
  id_especie: string;
  id_comunidade: string;
  tipoPedido: TipoPedido;
  status: StatusPedido;
  nomeRecebedor: string;
  contatoRecebedor: string;
  mensagemOpcional: string;
  quantidade: number;
  dataPedido: string;
}
