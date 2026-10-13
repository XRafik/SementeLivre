package com.sementelivre.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record PropriedadeRequestDTO(

        @Size(max = 150, message = "O nome pode ter no máximo 150 caracteres.")
        @NotBlank(message = "O nome da Propriedade é obrigatório.")
        String nome,
        @NotNull(message = "O tamanho do terreno não pode estar vazio") //Tenho duvidas se eu deveria deixar not null ou não já que talvez o prprietario possa não saber o tamnho
        @Positive(message = "O tamanho do terreno deve ser maior que zero")
        BigDecimal tamanhoHectares,
        // Logradouro já cadastrado; quem não tem o id (o front-app) manda o endereco
        UUID logradouroId,
        @NotNull(message = "O ID do proprietário ligado a esta propriedade é obrigatório.")
        UUID proprietarioId,
        @NotNull(message = "O ID da comunidade ligado a esta propriedade é obrigatório.")
        UUID comunidadeId,
        // Endereço da propriedade; vira um logradouro novo (o atual não é alterado)
        @Valid
        LogradouroDTO endereco
) {
    public PropriedadeRequestDTO(String nome, BigDecimal tamanhoHectares, UUID logradouroId,
            UUID proprietarioId, UUID comunidadeId) {
        this(nome, tamanhoHectares, logradouroId, proprietarioId, comunidadeId, null);
    }
}
