'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { criarRelatorio, listarRelatorios, type RelatorioDTO, type TipoRelatorioDTO } from '@/lib/relatorioApi';
import { useAuth } from '@/context/AuthContext';

export interface RelatorioEmitido {
  idRelatorio: string;
  tipo: TipoRelatorioDTO;
  filtros: Record<string, unknown>;
  dataGeracao: Date;
}

function paraRelatorio(dto: RelatorioDTO): RelatorioEmitido {
  return {
    idRelatorio: dto.id,
    tipo: dto.tipo,
    filtros: dto.filtrosUtilizados ?? {},
    dataGeracao: new Date(dto.dataGeracao),
  };
}

export function useRelatorios() {
  const { user } = useAuth();
  const [relatorios, setRelatorios] = useState<RelatorioEmitido[]>([]);
  const [loading, setLoading] = useState(false);
  // Emissões registradas nesta tela. Se o usuário gerar um relatório antes da
  // lista inicial chegar, a resposta (pedida antes da emissão) não traz o novo
  // e o apagaria do histórico; por isso ele é mesclado de volta.
  const emitidosAqui = useRef<RelatorioEmitido[]>([]);

  const recarregar = useCallback(async () => {
    if (!user) {
      emitidosAqui.current = [];
      setRelatorios([]);
      return;
    }
    setLoading(true);
    try {
      // O endpoint devolve todos; filtramos pelos do dono logado.
      const todos = await listarRelatorios();
      const doBackend = todos.filter((r) => r.proprietarioId === user.uid).map(paraRelatorio);
      const ids = new Set(doBackend.map((r) => r.idRelatorio));
      setRelatorios(
        [...doBackend, ...emitidosAqui.current.filter((r) => !ids.has(r.idRelatorio))]
          .sort((a, b) => b.dataGeracao.getTime() - a.dataGeracao.getTime())
      );
    } catch {
      setRelatorios([]);
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    void (async () => {
      await recarregar();
    })();
  }, [recarregar]);

  /** Registra no backend que o relatório foi emitido, com os filtros usados. */
  const registrarEmissao = useCallback(
    async (tipo: TipoRelatorioDTO, filtros: Record<string, unknown>): Promise<RelatorioEmitido> => {
      if (!user) throw new Error('Sessão expirada. Faça login novamente.');
      const dto = await criarRelatorio({ tipo, filtrosUtilizados: filtros, proprietarioId: user.uid });
      const registrado = paraRelatorio(dto);
      emitidosAqui.current = [registrado, ...emitidosAqui.current];
      setRelatorios((atuais) => [registrado, ...atuais]);
      return registrado;
    },
    [user]
  );

  return { relatorios, loading, recarregar, registrarEmissao };
}
