package com.sementelivre.backend.dto;

import java.util.UUID;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Pedido feito pelo site público, sem login.
 *
 * Só a oferta (estoque) e a quantidade vêm do navegador; produto, produtor,
 * preço e tipo do pedido são lidos do estoque no backend.
 */
public record PedidoPublicoRequestDTO(

        @NotNull(message = "A oferta é obrigatória")
        UUID estoqueId,

        @NotNull(message = "Quantidade é obrigatória")
        @Positive(message = "Quantidade deve ser maior que zero")
        Double quantidade,

        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String nome,

        @NotBlank(message = "O e-mail é obrigatório")
        @Email(message = "E-mail com formato inválido")
        @Size(max = 254, message = "O e-mail deve ter no máximo 254 caracteres")
        String email,

        @Size(max = 20, message = "O telefone deve ter no máximo 20 caracteres")
        String telefone,

        @Size(max = 2000, message = "A mensagem deve ter no máximo 2000 caracteres")
        String mensagemOpcional
) {
}
