package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sementelivre.backend.dto.ProdutorPublicoResponseDTO;
import com.sementelivre.backend.dto.SementePublicaResponseDTO;
import com.sementelivre.backend.service.ProdutorPublicoService;

/**
 * Endpoints publicos do perfil do produtor (issue #91), consumidos pelo
 * front-site.
 *
 * Rotas liberadas sem autenticacao em SecurityConfig
 * ({@code GET /produtores/**}). Todo o controle de visibilidade e LGPD fica no
 * ProdutorPublicoService e nos DTOs de dto/*Publico*; este controller nao
 * decide nada sobre exposicao de dado.
 *
 * Nao existe um GET /produtores (listagem de todos) nesta rodada: a #91 pede o
 * perfil de um produtor e as sementes dele. Uma vitrine com todos os produtores
 * publicos e um endpoint a mais, com paginacao propria, e foi deixada de fora
 * de proposito.
 */
@RestController
@RequestMapping("/produtores")
@Tag(name = "Produtor público", description = "Perfil público do produtor que aceitou aparecer no site (sem login).")
public class ProdutorPublicoController {

    private final ProdutorPublicoService produtorPublicoService;

    public ProdutorPublicoController(ProdutorPublicoService produtorPublicoService) {
        this.produtorPublicoService = produtorPublicoService;
    }

    /**
     * Perfil publico do produtor: identificacao, municipio, comunidades e as
     * sementes visiveis.
     *
     * Responde 404 tanto para produtor inexistente quanto para produtor que
     * nao optou por aparecer no site publico.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ProdutorPublicoResponseDTO> buscarPerfilPublico(@PathVariable UUID id) {
        return ResponseEntity.ok(produtorPublicoService.buscarPerfilPublico(id));
    }

    /**
     * Sementes/mudas publicamente disponiveis do produtor.
     *
     * Produtor publico sem sementes visiveis devolve 200 com lista vazia.
     */
    @GetMapping("/{id}/sementes")
    public ResponseEntity<List<SementePublicaResponseDTO>> listarSementes(@PathVariable UUID id) {
        return ResponseEntity.ok(produtorPublicoService.listarSementes(id));
    }
}
