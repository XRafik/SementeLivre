import { NextResponse } from "next/server";
import { randomUUID } from "crypto";
import { db } from "@/lib/store";

const sleep = () => new Promise((r) => setTimeout(r, 400));

export async function POST(request: Request) {
  await sleep();
  const body = await request.json();

  const pedido = {
    id_pedido: randomUUID(),
    id_especie: String(body.id_especie ?? ""),
    id_comunidade: String(body.id_comunidade ?? ""),
    tipoPedido: body.tipoPedido,
    status: "PENDENTE" as const,
    nomeRecebedor: String(body.nomeRecebedor ?? ""),
    contatoRecebedor: String(body.contatoRecebedor ?? ""),
    mensagemOpcional: String(body.mensagemOpcional ?? ""),
    quantidade: Number(body.quantidade ?? 1),
    dataPedido: new Date().toISOString(),
  };

  db.pedidos.push(pedido);

  return NextResponse.json(pedido, { status: 201 });
}
