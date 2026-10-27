package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sementelivre.backend.dto.CatalogoProdutoDetalheResponseDTO;
import com.sementelivre.backend.dto.CatalogoProdutoResponseDTO;
import com.sementelivre.backend.entity.enums.Disponibilidade;
import com.sementelivre.backend.entity.enums.EspecieGeral;
import com.sementelivre.backend.entity.enums.TipoProduto;
import com.sementelivre.backend.service.CatalogoPublicoService;

@RestController
@RequestMapping("/catalogo/produtos")
@Tag(name = "Catálogo público", description = "Produtos e ofertas exibidos no site público (sem login).")
public class CatalogoPublicoController {

    private final CatalogoPublicoService catalogoPublicoService;

    public CatalogoPublicoController(
            CatalogoPublicoService catalogoPublicoService) {
        this.catalogoPublicoService = catalogoPublicoService;
    }

    @GetMapping
    public ResponseEntity<Page<CatalogoProdutoResponseDTO>> listar(
            @RequestParam(required = false) String nomePopular,
            @RequestParam(required = false) TipoProduto tipo,
            @RequestParam(required = false) EspecieGeral especie,
            @RequestParam(required = false) Disponibilidade disponibilidade,
            @RequestParam(required = false) String comunidade,
            @RequestParam(required = false) String municipio,
            Pageable pageable) {

        return ResponseEntity.ok(
                catalogoPublicoService.listar(
                        nomePopular,
                        tipo,
                        especie,
                        disponibilidade,
                        comunidade,
                        municipio,
                        pageable
                )
        );
    }

        @GetMapping("/{id}")
    public ResponseEntity<CatalogoProdutoDetalheResponseDTO> buscarPorId(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                catalogoPublicoService.buscarPorProdutoId(id)
        );
    }
}
