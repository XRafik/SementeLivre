'use client';

import { useEffect, useState, useCallback } from 'react';
import { Propriedade, Comunidade, StatusComunidade } from '@/types/property';
import { useAuth } from '@/context/AuthContext';
import {
  atualizarPropriedade, ComunidadeDTO, criarPropriedade, excluirPropriedade, listarComunidades,
  listarPropriedades, PropriedadeDTO, PropriedadeRequestDTO, solicitarComunidade,
} from '@/lib/propriedadeApi';

function paraPropriedade(dto: PropriedadeDTO): Propriedade {
  const end = dto.endereco;
  return {
    idPropriedade: dto.id,
    idProprietario: dto.proprietarioId,
    idComunidade: dto.comunidadeId,
    nomeComunidade: dto.comunidadeNome,
    nome: dto.nome,
    tamanhoHectares: dto.tamanhoHectares,
    logradouro: end?.logradouro ?? '',
    numero: end?.numero ?? '',
    complemento: end?.complemento ?? undefined,
    bairro: end?.bairro ?? '',
    municipio: end?.municipio ?? dto.logradouroMunicipio ?? '',
    uf: end?.uf ?? dto.logradouroUf ?? '',
    cep: end?.cep ?? '',
    dataCadastro: new Date(dto.dataCadastro),
    dataUltimaAlteracao: new Date(dto.dataUltimaAlteracao),
  };
}

function paraRequest(p: Omit<Propriedade, 'idPropriedade'>): PropriedadeRequestDTO {
  const opcional = (v?: string) => v?.trim() || undefined;
  return {
    nome: p.nome.trim(),
    tamanhoHectares: p.tamanhoHectares ?? 0,
    proprietarioId: p.idProprietario,
    comunidadeId: p.idComunidade,
    endereco: {
      logradouro: p.logradouro.trim(),
      numero: opcional(p.numero),
      complemento: opcional(p.complemento),
      bairro: opcional(p.bairro),
      municipio: p.municipio.trim(),
      uf: p.uf,
      cep: opcional(p.cep?.replace(/\D/g, '')),
    },
  };
}

function paraComunidade(dto: ComunidadeDTO): Comunidade {
  return {
    idComunidade: dto.id,
    nome: dto.nome,
    municipio: dto.logradouroMunicipio ?? '',
    uf: dto.logradouroUf ?? '',
    status: dto.status,
    dataSolicitacao: new Date(dto.dataSolicitacao),
    dataAprovacao: dto.dataAprovacao ? new Date(dto.dataAprovacao) : undefined,
  };
}

/** Propriedades do proprietário logado, vindas de /api/propriedades. */
export function useProperties() {
  const { user } = useAuth();
  const [properties, setProperties] = useState<Propriedade[]>([]);
  const [loading, setLoading] = useState(true);

  const recarregar = useCallback(async () => {
    if (!user) {
      setProperties([]);
      setLoading(false);
      return;
    }
    try {
      // O backend já devolve só as do dono logado, mais recentes primeiro
      setProperties((await listarPropriedades()).map(paraPropriedade));
    } catch {
      // Sem conexão já vira toast em lib/api.ts; a lista fica como estava
    } finally {
      setLoading(false);
    }
  }, [user]);

  useEffect(() => {
    let cancelado = false;
    void (async () => {
      await recarregar();
      if (cancelado) return;
    })();
    return () => {
      cancelado = true;
    };
  }, [recarregar]);

  const createProperty = useCallback(
    async (data: Omit<Propriedade, 'idPropriedade'>): Promise<string> => {
      const criada = await criarPropriedade(paraRequest(data));
      setProperties((prev) => [paraPropriedade(criada), ...prev]);
      return criada.id;
    },
    []
  );

  const updateProperty = useCallback(
    async (id: string, data: Partial<Propriedade>) => {
      const atual = properties.find((p) => p.idPropriedade === id);
      if (!atual) throw new Error('Propriedade não encontrada.');
      const salva = paraPropriedade(await atualizarPropriedade(id, paraRequest({ ...atual, ...data })));
      setProperties((prev) => prev.map((p) => (p.idPropriedade === id ? salva : p)));
    },
    [properties]
  );

  const deleteProperty = useCallback(async (id: string) => {
    await excluirPropriedade(id);
    setProperties((prev) => prev.filter((p) => p.idPropriedade !== id));
  }, []);

  return { properties, loading, createProperty, updateProperty, deleteProperty, reload: recarregar };
}

/**
 * Comunidades que o proprietário pode escolher: as ativas e as que ele mesmo
 * acabou de solicitar (ficam pendentes até o admin aprovar).
 */
export function useCommunities() {
  const [communities, setCommunities] = useState<Comunidade[]>([]);

  useEffect(() => {
    let cancelado = false;
    listarComunidades()
      .then((lista) => {
        if (cancelado) return;
        setCommunities(lista.filter((c) => c.status === StatusComunidade.ATIVA).map(paraComunidade));
      })
      .catch(() => undefined);
    return () => {
      cancelado = true;
    };
  }, []);

  const requestCommunity = useCallback(
    async (nome: string, localidade: string, municipio: string, uf: string): Promise<string> => {
      const criada = paraComunidade(
        await solicitarComunidade({ nome: nome.trim(), endereco: { logradouro: localidade.trim(), municipio: municipio.trim(), uf } })
      );
      setCommunities((prev) => [...prev, criada]);
      return criada.idComunidade;
    },
    []
  );

  return { communities, requestCommunity };
}
