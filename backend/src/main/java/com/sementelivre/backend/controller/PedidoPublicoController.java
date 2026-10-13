package com.sementelivre.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sementelivre.backend.dto.PedidoPublicoRequestDTO;
import com.sementelivre.backend.dto.PedidoPublicoResponseDTO;
import com.sementelivre.backend.service.PedidoService;

import jakarta.validation.Valid;

/** Pedido feito pelo site público (front-site), sem login. */
@RestController
@RequestMapping("/catalogo/pedidos")
public class PedidoPublicoController {

    private final PedidoService pedidoService;

    public PedidoPublicoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    @PostMapping
    public ResponseEntity<PedidoPublicoResponseDTO> criar(
            @Valid @RequestBody PedidoPublicoRequestDTO pedido) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pedidoService.criarPublico(pedido));
    }
}
