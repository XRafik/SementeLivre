"use client";

import { useEffect, useState } from "react";
import Image from "next/image";
import { ChevronLeft, ChevronRight, Pause, Play } from "lucide-react";
import type { FotoComunidade } from "@/lib/divulgacao";

const INTERVALO_MS = 5000;

/**
 * Carrossel de fotos das comunidades (#181), no padrão "Auto-Rotating Image
 * Carousel" do WAI-ARIA APG: botão de pausar primeiro, slides com
 * aria-roledescription, e a região só é anunciada quando não está girando
 * sozinha. Pausa com o mouse ou o foco dentro e não gira para quem pediu
 * menos movimento no sistema.
 */
export default function CarrosselComunidades({ fotos }: { fotos: FotoComunidade[] }) {
  const [atual, setAtual] = useState(0);
  const [pausadoPeloUsuario, setPausadoPeloUsuario] = useState(false);
  const [emUso, setEmUso] = useState(false);
  const [menosMovimento, setMenosMovimento] = useState(false);

  useEffect(() => {
    const consulta = window.matchMedia("(prefers-reduced-motion: reduce)");
    const atualizar = () => setMenosMovimento(consulta.matches);
    atualizar();
    consulta.addEventListener("change", atualizar);
    return () => consulta.removeEventListener("change", atualizar);
  }, []);

  const girando = !pausadoPeloUsuario && !emUso && !menosMovimento && fotos.length > 1;

  useEffect(() => {
    if (!girando) return;
    const timer = setInterval(() => setAtual((i) => (i + 1) % fotos.length), INTERVALO_MS);
    return () => clearInterval(timer);
  }, [girando, fotos.length]);

  if (fotos.length === 0) return null;

  const irPara = (i: number) => setAtual((i + fotos.length) % fotos.length);

  return (
    <section
      aria-roledescription="carrossel"
      aria-label="Fotos das comunidades"
      className="relative w-full overflow-hidden rounded-2xl bg-foreground shadow-lg"
      onMouseEnter={() => setEmUso(true)}
      onMouseLeave={() => setEmUso(false)}
      onFocus={() => setEmUso(true)}
      onBlur={(e) => {
        if (!e.currentTarget.contains(e.relatedTarget as Node | null)) setEmUso(false);
      }}
    >
      <div
        id="carrossel-comunidades-slides"
        aria-live={girando ? "off" : "polite"}
        className="relative aspect-[4/3] sm:aspect-[21/9]"
      >
        {fotos.map((foto, i) => (
          <div
            key={foto.src}
            role="group"
            aria-roledescription="slide"
            aria-label={`${i + 1} de ${fotos.length}`}
            hidden={i !== atual}
            className="absolute inset-0"
          >
            <Image
              src={foto.src}
              alt={foto.alt}
              fill
              sizes="(min-width: 1280px) 1216px, 100vw"
              className="object-cover"
              priority={i === 0}
            />
            <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/70 to-transparent px-5 pb-12 pt-16 sm:px-8">
              <p className="text-white text-sm sm:text-lg font-medium max-w-2xl">{foto.legenda}</p>
            </div>
          </div>
        ))}
      </div>

      <div className="absolute inset-x-0 bottom-3 flex items-center justify-between gap-2 px-3 sm:px-5">
        <button
          type="button"
          onClick={() => setPausadoPeloUsuario((p) => !p)}
          aria-label={pausadoPeloUsuario ? "Iniciar rotação automática das fotos" : "Pausar rotação automática das fotos"}
          className="flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-foreground hover:bg-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
        >
          {pausadoPeloUsuario ? <Play className="h-4 w-4" aria-hidden="true" /> : <Pause className="h-4 w-4" aria-hidden="true" />}
        </button>

        <div className="flex items-center gap-1.5">
          {fotos.map((foto, i) => (
            <button
              key={foto.src}
              type="button"
              onClick={() => irPara(i)}
              aria-label={`Mostrar foto ${i + 1}: ${foto.legenda}`}
              aria-current={i === atual ? "true" : undefined}
              aria-controls="carrossel-comunidades-slides"
              className={`h-2.5 rounded-full transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-white ${
                i === atual ? "w-6 bg-white" : "w-2.5 bg-white/50 hover:bg-white/80"
              }`}
            />
          ))}
        </div>

        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => irPara(atual - 1)}
            aria-label="Foto anterior"
            aria-controls="carrossel-comunidades-slides"
            className="flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-foreground hover:bg-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ChevronLeft className="h-5 w-5" aria-hidden="true" />
          </button>
          <button
            type="button"
            onClick={() => irPara(atual + 1)}
            aria-label="Próxima foto"
            aria-controls="carrossel-comunidades-slides"
            className="flex h-9 w-9 items-center justify-center rounded-full bg-white/90 text-foreground hover:bg-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <ChevronRight className="h-5 w-5" aria-hidden="true" />
          </button>
        </div>
      </div>
    </section>
  );
}
