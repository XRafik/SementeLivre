"use client";

import { useState } from "react";
import { Leaf } from "lucide-react";

/** Foto do produto; sem foto, ou se ela não carregar, mostra a folha. */
export default function FotoProduto({
  src,
  alt,
  iconClassName,
}: {
  src: string | null;
  alt: string;
  iconClassName: string;
}) {
  const [falhou, setFalhou] = useState(false);

  if (!src || falhou) {
    return <Leaf className={iconClassName} />;
  }

  return (
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt={alt} className="h-full w-full object-cover" onError={() => setFalhou(true)} />
  );
}
