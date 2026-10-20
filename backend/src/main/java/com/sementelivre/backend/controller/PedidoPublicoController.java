package com.sementelivre.backend.controller;

import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.sementelivre.backend.dto.PedidoPublicoRequestDTO;
import com.sementelivre.backend.dto.PedidoPublicoResponseDTO;
import com.sementelivre.backend.service.LimitePedidoPublicoService;
import com.sementelivre.backend.service.PedidoService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/** Pedido feito pelo site público (front-site), sem login. */
@RestController
@RequestMapping("/catalogo/pedidos")
@Tag(name = "Pedido público", description = "Pedido feito pelo site público, sem conta; limitado por e-mail e IP.")
public class PedidoPublicoController {

    private final PedidoService pedidoService;
    private final LimitePedidoPublicoService limite;

    public PedidoPublicoController(PedidoService pedidoService, LimitePedidoPublicoService limite) {
        this.pedidoService = pedidoService;
        this.limite = limite;
    }

    @PostMapping
    public ResponseEntity<PedidoPublicoResponseDTO> criar(
            @Valid @RequestBody PedidoPublicoRequestDTO pedido,
            HttpServletRequest request) {

        // Sem login, cada pedido manda e-mail para o endereço informado: sem
        // limite, o formulário vira um jeito de inundar a caixa de alguém.
        limite.verificar(request, pedido.email());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(pedidoService.criarPublico(pedido));
    }
}
