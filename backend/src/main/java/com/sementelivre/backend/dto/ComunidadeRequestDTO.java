package com.sementelivre.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record ComunidadeRequestDTO(
        @NotBlank(message = "Nome da Comunidade é obrigatório.")
        @Size(max = 150, message = "O nome pode ter no máximo 150 caracteres.")
        String nome,
        // Logradouro já cadastrado; quem não tem o id (o front-app) manda o endereco
        UUID logradouroId,
        // Onde fica a comunidade; vira um logradouro novo (o atual não é alterado)
        @Valid
        LogradouroDTO endereco
) {
    public ComunidadeRequestDTO(String nome, UUID logradouroId) {
        this(nome, logradouroId, null);
    }
}
