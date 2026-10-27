package com.sementelivre.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PropriedadeResponseDTO(

        UUID id,
        String nome,
        BigDecimal tamanhoHectares,
        LocalDateTime dataCadastro,
        LocalDateTime dataUltimaAlteracao,
        //Dados do Proprietario relacionado
        UUID proprietarioId,
        String proprietarioNome,
        //Dados da Comunidade relacionada
        UUID comunidadeId,
        String comunidadeNome,
        //Dados do LOgradouro relacionado
        String logradouroUf,
        String logradouroMunicipio,
        //Endereço completo, para a tela de detalhe
        LogradouroDTO endereco
) {
}
