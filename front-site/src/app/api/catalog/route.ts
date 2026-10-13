import { NextRequest, NextResponse } from "next/server";
import { db } from "@/lib/store";

const sleep = () => new Promise((r) => setTimeout(r, 400));

export async function GET(request: NextRequest) {
  await sleep();
  const id_comunidade = request.nextUrl.searchParams.get("id_comunidade");
  if (id_comunidade) {
    return NextResponse.json(
      db.species.filter((s) => s.id_comunidade === id_comunidade)
    );
  }
  return NextResponse.json(db.species);
}
