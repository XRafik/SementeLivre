package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.sementelivre.backend.dto.PedidoFiltroDTO;
import com.sementelivre.backend.dto.PedidoRequestDTO;
import com.sementelivre.backend.dto.PedidoResponseDTO;
import com.sementelivre.backend.dto.PedidoUpdateDTO;
import com.sementelivre.backend.entity.Usuario;
import com.sementelivre.backend.entity.enums.StatusPedido;
import com.sementelivre.backend.entity.enums.TipoPedido;
import com.sementelivre.backend.service.PedidoService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/pedidos")
@Tag(name = "Pedidos", description = "Pedidos do proprietário: registrar, confirmar, cancelar e histórico.")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    // CREATE
    // O produtor registra pedido so no proprio estoque, e o solicitante e sempre o
    // usuario logado: um usuarioSolicitanteId vindo no corpo e ignorado.
    @PostMapping
    @PreAuthorize("hasRole('ADMIN') or #pedido.proprietarioRecebedorId() == principal.id")
    public ResponseEntity<PedidoResponseDTO> criar(
            @Valid @RequestBody PedidoRequestDTO pedido,
            @AuthenticationPrincipal Usuario usuarioAutenticado) {

        return ResponseEntity.ok(
                pedidoService.criar(pedido.comUsuarioSolicitante(usuarioAutenticado.getId()))
        );
    }

    // READ - historico de pedidos de um proprietario, com filtros opcionais
    // ex: /pedidos?proprietarioId=...&dataInicio=2026-09-01&dataFim=2026-09-30&tipoPedido=VENDA&status=PENDENTE
    // O proprietario so ve o proprio historico (os pedidos trazem nome e contato de quem pediu)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN') or #proprietarioId == principal.id")
    public ResponseEntity<List<PedidoResponseDTO>> listarTodos(
            @RequestParam UUID proprietarioId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) TipoPedido tipoPedido,
            @RequestParam(required = false) UUID produtoId,
            @RequestParam(required = false) StatusPedido status) {

        PedidoFiltroDTO filtro = new PedidoFiltroDTO(dataInicio, dataFim, tipoPedido, produtoId, status);

        return ResponseEntity.ok(
                pedidoService.listarTodos(proprietarioId, filtro)
        );
    }

    // READ - por ID (admin ou quem recebeu o pedido)
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @pedidoService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<PedidoResponseDTO> buscarPorId(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                pedidoService.buscarPorId(id)
        );
    }

    // UPDATE
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @pedidoService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<PedidoResponseDTO> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody PedidoUpdateDTO pedido) {

        return ResponseEntity.ok(
                pedidoService.atualizar(id, pedido)
        );
    }

    // DELETE
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or @pedidoService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<Void> excluir(@PathVariable UUID id) {

        pedidoService.excluir(id);

        return ResponseEntity.noContent().build();
    }

    // CICLO DE VIDA
    // Endpoints proprios em vez de aceitar status no PUT: sao eles que
    // disparam a baixa e a restauracao do estoque.

    @PatchMapping("/{id}/confirmar")
    @PreAuthorize("hasRole('ADMIN') or @pedidoService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<PedidoResponseDTO> confirmar(@PathVariable UUID id) {

        return ResponseEntity.ok(
                pedidoService.confirmar(id)
        );
    }

    @PatchMapping("/{id}/cancelar")
    @PreAuthorize("hasRole('ADMIN') or @pedidoService.ehDoProprietario(#id, principal.id)")
    public ResponseEntity<PedidoResponseDTO> cancelar(@PathVariable UUID id) {

        return ResponseEntity.ok(
                pedidoService.cancelar(id)
        );
    }
}
