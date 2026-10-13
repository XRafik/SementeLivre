import type { Species, Comunidade, Pedido } from "./types";

interface DbSchema {
  species: Species[];
  comunidades: Comunidade[];
  pedidos: Pedido[];
}

declare global {
  // eslint-disable-next-line no-var
  var __sementesDb_v8: DbSchema | undefined;
}

// Key is versioned — bump when schema changes to avoid stale cached objects
const db: DbSchema =
  globalThis.__sementesDb_v8 ??
  (globalThis.__sementesDb_v8 = {
    species: [
      {
        id_especie: "esp-1",
        nome_popular: "Feijão Crioulo",
        nome_cientifico: "Phaseolus vulgaris",
        familia_botanica: "Fabaceae",
        descricao:
          "Variedade tradicional de feijão cultivada há gerações no quilombo, adaptada ao clima local e com alto valor nutricional.",
        foto: "/sementes/feijao-crioulo.png",
        status: "exchange",
        id_comunidade: "com-1",
        tipoSemente: "LEGUMINOSA",
        quantidadeEstoque: 50,
        preco: 15,
        unidadePesagem: "KG",
      },
      {
        id_especie: "esp-2",
        nome_popular: "Milho Caiano",
        nome_cientifico: "Zea mays",
        familia_botanica: "Poaceae",
        descricao:
          "Milho crioulo de ciclo curto, resistente à seca e com grãos amarelo-claros ideais para fubá e canjica.",
        foto: "/sementes/milho-caiano.png",
        status: "sale",
        id_comunidade: "com-1",
        tipoSemente: "CEREAL",
        quantidadeEstoque: 30,
        preco: 20,
        unidadePesagem: "KG",
      },
      {
        id_especie: "esp-3",
        nome_popular: "Abóbora Cabotiá",
        nome_cientifico: "Cucurbita maxima",
        familia_botanica: "Cucurbitaceae",
        descricao:
          "Abóbora de polpa firme e adocicada, excelente para doces e pratos salgados. Produção abundante na região.",
        foto: "/sementes/abobora-cabotia.png",
        status: "donation",
        id_comunidade: "com-1",
        tipoSemente: "HORTALICA",
        quantidadeEstoque: 100,
        unidadePesagem: "UNIDADE",
      },
      {
        id_especie: "esp-4",
        nome_popular: "Quiabo Vermelho",
        nome_cientifico: "Abelmoschus esculentus",
        familia_botanica: "Malvaceae",
        descricao:
          "Variedade rara de quiabo com frutos avermelhados, rica em antioxidantes. Estoque temporariamente esgotado.",
        foto: "/sementes/quiabo-vermelho.png",
        status: "unavailable",
        id_comunidade: "com-1",
        tipoSemente: "HORTALICA",
        quantidadeEstoque: 0,
        unidadePesagem: "KG",
      },
      {
        id_especie: "esp-5",
        nome_popular: "Maxixe do Norte",
        nome_cientifico: "Cucumis anguria",
        familia_botanica: "Cucurbitaceae",
        descricao:
          "Fruto nativo de sabor levemente amargo, muito usado em saladas e refogados na culinária regional nordestina.",
        foto: "/sementes/maxixe-do-norte.png",
        status: "sale",
        id_comunidade: "com-2",
        tipoSemente: "HORTALICA",
        quantidadeEstoque: 80,
        preco: 5,
        unidadePesagem: "KG",
      },
    ],

    // ── Multi-community ────────────────────────────────────────────────────
    comunidades: [
      {
        id_comunidade: "com-1",
        nome: "Quilombo dos Coelhos",
        localizacao: "Pernambuco - PE",
        status: "ativa",
      },
      {
        id_comunidade: "com-2",
        nome: "Quilombo Terra Livre",
        localizacao: "Bahia - BA",
        status: "ativa",
      },
      {
        id_comunidade: "com-3",
        nome: "Comunidade Zumbi dos Palmares",
        localizacao: "Alagoas - AL",
        status: "ativa",
      },
    ],
    pedidos: [],
  });

export { db };

