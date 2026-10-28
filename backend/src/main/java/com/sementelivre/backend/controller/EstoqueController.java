package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sementelivre.backend.dto.EstoqueRequestDTO;
import com.sementelivre.backend.dto.EstoqueResponseDTO;
import com.sementelivre.backend.dto.MovimentacaoRequestDTO;
import com.sementelivre.backend.dto.MovimentacaoResponseDTO;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.service.EstoqueService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/estoques")
@Tag(name = "Estoque", description = "Ofertas de cada proprietário (quantidade, preço, disponibilidade) e movimentações.")
public class EstoqueController {

    private final EstoqueService estoqueService;

    public EstoqueController(EstoqueService estoqueService) {
        this.estoqueService = estoqueService;
    }

    // CREATE - o produtor só cadastra oferta para si, de um produto que pode usar
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or (#estoque.proprietarioId() == principal.id"
            + " and @produtoService.podeAlterar(#estoque.produtoId(), principal.id))")
    public ResponseEntity<EstoqueResponseDTO> criar(
            @Valid @RequestBody EstoqueRequestDTO estoque) {

        return ResponseEntity.ok(
                estoqueService.criar(estoque)
        );
    }

    // READ - admin lista todos; o proprietário, só os dele
    @GetMapping
    public ResponseEntity<List<EstoqueResponseDTO>> listarTodos(
            @AuthenticationPrincipal Usuario usuario) {

        boolean admin = usuario.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));

        return ResponseEntity.ok(admin
                ? estoqueService.listarTodos()
                : estoqueService.listarPorProprietario(usuario.getId()));
    }

    // READ - por ID (admin ou o dono)
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @estoqueService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<EstoqueResponseDTO> buscarPorId(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                estoqueService.buscarPorId(id)
        );
    }

    // UPDATE - o dono não pode passar a oferta para outra pessoa
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or (@estoqueService.ehDoProprietario(#id, principal.id)"
            + " and #estoque.proprietarioId() == principal.id"
            + " and @produtoService.podeAlterar(#estoque.produtoId(), principal.id))")
    public ResponseEntity<EstoqueResponseDTO> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody EstoqueRequestDTO estoque) {

        return ResponseEntity.ok(
                estoqueService.atualizar(id, estoque)
        );
    }

    // DELETE (admin ou o dono)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @estoqueService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {

        estoqueService.excluir(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * Histórico do estoque, do mais recente para o mais antigo (TELA 09).
     */
    @GetMapping("/{id}/movimentacoes")
    @PreAuthorize("hasRole('ADMIN') or @estoqueService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<Page<MovimentacaoResponseDTO>> historico(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, Math.min(size, 100));

        return ResponseEntity.ok(estoqueService.historico(id, pageable));
    }

    /**
     * Lança uma entrada ou saída e ajusta o saldo na mesma transação.
     */
    @PostMapping("/{id}/movimentacoes")
    @PreAuthorize("hasRole('ADMIN') or @estoqueService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<MovimentacaoResponseDTO> registrarMovimentacao(
            @PathVariable UUID id,
            @Valid @RequestBody MovimentacaoRequestDTO movimentacao) {

        MovimentacaoResponseDTO registrada = estoqueService.registrarMovimentacao(id, movimentacao);

        return ResponseEntity.status(HttpStatus.CREATED).body(registrada);
    }
}