import type { Disponibilidade, Pesagem, TipoPedido, TipoProduto } from "./types";

export const DISPONIBILIDADE_CONFIG: Record<
  Disponibilidade,
  { label: string; className: string; tipoPedido: TipoPedido }
> = {
  PARA_VENDA: {
    label: "Para Venda",
    className: "bg-primary-light text-primary-dark border-transparent",
    tipoPedido: "VENDA",
  },
  PARA_TROCA: {
    label: "Para Troca",
    className: "bg-amber-100 text-amber-800 border-transparent",
    tipoPedido: "TROCA",
  },
  PARA_DOACAO: {
    label: "Para Doação",
    className: "bg-blue-100 text-blue-900 border-transparent",
    tipoPedido: "DOACAO",
  },
};

export const TIPO_PRODUTO_LABEL: Record<TipoProduto, string> = {
  HORTALICA: "Hortaliça",
  FRUTIFERA: "Frutífera",
  FORRAGEIRA: "Forrageira",
  CEREAL: "Cereal",
  LEGUMINOSA: "Leguminosa",
  VERDURA: "Verdura",
  MEDICINAL: "Medicinal",
  OUTRAS: "Outras",
};

export const TIPO_PEDIDO_LABEL: Record<TipoPedido, string> = {
  VENDA: "Venda",
  TROCA: "Troca",
  DOACAO: "Doação",
};

export const PESAGEM_LABEL: Record<Pesagem, string> = {
  SACA: "saca",
  KG: "kg",
  GRAMA: "g",
  MG: "mg",
  UNIDADE: "unid.",
};

export function unidade(pesagem: Pesagem | null): string {
  return pesagem ? PESAGEM_LABEL[pesagem] : "unid.";
}

/** Mensagem de erro do backend: os erros por campo quando houver, senão a mensagem geral. */
export function mensagemDeErro(body: unknown, fallback: string): string {
  if (body && typeof body === "object") {
    const b = body as { message?: string; fieldErrors?: string[] | null };
    if (b.fieldErrors?.length) {
      return b.fieldErrors.map((e) => e.slice(e.indexOf(": ") + 2)).join(" ");
    }
    if (b.message) return b.message;
  }
  return fallback;
}
