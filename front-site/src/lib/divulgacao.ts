// Divulgação na página inicial (#181): link do app e fotos das comunidades.

// O app (front-app) ainda não tem endereço fixo; cada ambiente informa o seu.
export const APP_URL = process.env.NEXT_PUBLIC_APP_URL ?? "http://localhost:3000";

export interface FotoComunidade {
  src: string;
  /** O que aparece na foto, para quem usa leitor de tela. */
  alt: string;
  legenda: string;
}

// Fotos provisórias, do próprio catálogo. Para trocar: coloque as fotos das
// comunidades em public/comunidades/ e aponte o src para "/comunidades/<arquivo>".
export const FOTOS_COMUNIDADES: FotoComunidade[] = [
  {
    src: "/sementes/feijao-crioulo.png",
    alt: "Grãos de feijão crioulo de várias cores",
    legenda: "Feijão crioulo guardado e multiplicado pelas famílias da rede",
  },
  {
    src: "/sementes/milho-caiano.png",
    alt: "Espigas de milho caiano",
    legenda: "Milho caiano, cultivado há gerações nas comunidades quilombolas",
  },
  {
    src: "/sementes/abobora-cabotia.png",
    alt: "Abóboras cabotiá colhidas",
    legenda: "Abóbora cabotiá da colheita comunitária",
  },
  {
    src: "/sementes/maxixe-do-norte.png",
    alt: "Maxixes do norte",
    legenda: "Maxixe do norte, adaptado ao clima da região",
  },
  {
    src: "/sementes/quiabo-vermelho.png",
    alt: "Quiabos vermelhos",
    legenda: "Quiabo vermelho, variedade preservada pela rede",
  },
];
