package com.sementelivre.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompradorDTO(

        @NotBlank(message = "Nome do comprador é obrigatório")
        @Size(max = 150, message = "Nome do comprador deve ter no máximo 150 caracteres")
        String nome,

        @Size(max = 20, message = "Telefone do comprador deve ter no máximo 20 caracteres")
        String telefone,

        // Preenchido nos pedidos do site público: é o contato de quem pediu
        @Email(message = "E-mail do comprador com formato inválido")
        @Size(max = 254, message = "E-mail do comprador deve ter no máximo 254 caracteres")
        String email

) {
    public CompradorDTO(String nome, String telefone) {
        this(nome, telefone, null);
    }
}
