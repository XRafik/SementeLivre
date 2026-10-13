package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.sementelivre.backend.dto.ContagemNaoLidasDTO;
import com.sementelivre.backend.dto.NotificacaoRequestDTO;
import com.sementelivre.backend.dto.NotificacaoResponseDTO;
import com.sementelivre.backend.service.NotificacaoService;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/notificacoes")
@Tag(name = "Notificações", description = "Notificações geradas pelos pedidos (CDU-26).")
public class NotificacaoController {

    private final NotificacaoService notificacaoService;

    public NotificacaoController(NotificacaoService notificacaoService) {
        this.notificacaoService = notificacaoService;
    }

    // CREATE
    @PostMapping
    public ResponseEntity<NotificacaoResponseDTO> criar(
            @Valid @RequestBody NotificacaoRequestDTO notificacao) {

        return ResponseEntity.status(HttpStatus.CREATED).body(
                notificacaoService.criar(notificacao)
        );
    }

    // READ - todas, ou so as de um proprietario (mais recentes primeiro)
    @GetMapping
    public ResponseEntity<List<NotificacaoResponseDTO>> listar(
            @RequestParam(required = false) UUID proprietarioId) {

        if (proprietarioId != null) {
            return ResponseEntity.ok(
                    notificacaoService.listarPorProprietario(proprietarioId)
            );
        }

        return ResponseEntity.ok(
                notificacaoService.listar()
        );
    }

    /**
     * Quantidade de não lidas, para o badge do cabeçalho.
     *
     * Contar no banco em vez de baixar a lista inteira evita tráfego desnecessário
     * a cada navegação.
     */
    @GetMapping("/nao-lidas/contagem")
    public ResponseEntity<ContagemNaoLidasDTO> contarNaoLidas() {

        return ResponseEntity.ok(
                new ContagemNaoLidasDTO(notificacaoService.contarNaoLidas())
        );
    }

    // READ - por ID
    @GetMapping("/{id}")
    public ResponseEntity<NotificacaoResponseDTO> buscarPorId(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                notificacaoService.buscarPorId(id)
        );
    }

    // UPDATE
    @PutMapping("/{id}")
    public ResponseEntity<NotificacaoResponseDTO> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody NotificacaoRequestDTO notificacao) {

        return ResponseEntity.ok(
                notificacaoService.atualizar(id, notificacao)
        );
    }

    // UPDATE - marcar como lida
    @PatchMapping("/{id}/lida")
    public ResponseEntity<NotificacaoResponseDTO> marcarComoLida(
            @PathVariable UUID id) {

        return ResponseEntity.ok(
                notificacaoService.marcarComoLida(id)
        );
    }

    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {

        notificacaoService.deletar(id);

        return ResponseEntity.noContent().build();
    }
}
