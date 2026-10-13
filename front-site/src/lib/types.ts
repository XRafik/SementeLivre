// Espelham os DTOs do catálogo público do backend (/catalogo/produtos, /catalogo/pedidos).

export type Disponibilidade = "PARA_VENDA" | "PARA_TROCA" | "PARA_DOACAO";
export type TipoProduto =
  | "HORTALICA"
  | "FRUTIFERA"
  | "FORRAGEIRA"
  | "CEREAL"
  | "LEGUMINOSA"
  | "VERDURA"
  | "MEDICINAL"
  | "OUTRAS";
export type Pesagem = "SACA" | "KG" | "GRAMA" | "MG" | "UNIDADE";
export type TipoPedido = "VENDA" | "TROCA" | "DOACAO";

/** Uma oferta do catálogo: um produto no estoque de um produtor. */
export interface OfertaCatalogo {
  id: string; // id do produto
  nomePopular: string;
  nomeCientifico: string | null;
  historico: string | null;
  urlFoto: string | null;
  tipo: TipoProduto | null;
  familiaBotanica: string | null;
  estoqueId: string;
  descricao: string | null;
  preco: number | null;
  quantidade: number | null;
  tipoPesagem: Pesagem | null;
  disponibilidade: Disponibilidade;
  comunidadeId: string | null;
  comunidade: string | null;
  municipio: string | null;
  uf: string | null;
}

export interface Oferta {
  estoqueId: string;
  descricao: string | null;
  preco: number | null;
  quantidade: number | null;
  tipoPesagem: Pesagem | null;
  disponibilidade: Disponibilidade;
}

export interface ProdutoDetalhe {
  id: string;
  nomePopular: string;
  nomeCientifico: string | null;
  historico: string | null;
  urlFoto: string | null;
  tipo: TipoProduto | null;
  familiaBotanica: string | null;
  comunidadeId: string | null;
  comunidade: string | null;
  municipio: string | null;
  uf: string | null;
  ofertas: Oferta[];
}

export interface PedidoPublicoRequest {
  estoqueId: string;
  quantidade: number;
  nome: string;
  email: string;
  telefone: string;
  mensagemOpcional: string;
}
