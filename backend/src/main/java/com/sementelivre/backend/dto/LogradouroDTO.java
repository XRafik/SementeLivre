package com.sementelivre.backend.dto;

import com.sementelivre.backend.entity.Logradouro;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class LogradouroDTO {

    // Tamanhos iguais aos de logradouro_t (V1_1).
    @NotBlank(message = "O logradouro é obrigatório")
    @Size(max = 255, message = "O logradouro deve ter no máximo 255 caracteres")
    private String logradouro;

    @Size(max = 10, message = "O número deve ter no máximo 10 caracteres")
    private String numero;

    @Size(max = 100, message = "O complemento deve ter no máximo 100 caracteres")
    private String complemento;

    @Size(max = 100, message = "O bairro deve ter no máximo 100 caracteres")
    private String bairro;

    @NotBlank(message = "O município é obrigatório")
    @Size(max = 100, message = "O município deve ter no máximo 100 caracteres")
    private String municipio;

    @NotBlank(message = "A UF é obrigatória")
    @Pattern(regexp = "^[A-Z]{2}$", message = "A UF deve conter exatamente 2 letras maiúsculas")
    private String uf;

    @Size(max = 9, message = "O CEP deve ter no máximo 9 caracteres")
    private String cep;

    public String getLogradouro() {
        return logradouro;
    }

    public void setLogradouro(String logradouro) {
        this.logradouro = logradouro;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getComplemento() {
        return complemento;
    }

    public void setComplemento(String complemento) {
        this.complemento = complemento;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getMunicipio() {
        return municipio;
    }

    public void setMunicipio(String municipio) {
        this.municipio = municipio;
    }

    public String getUf() {
        return uf;
    }

    public void setUf(String uf) {
        this.uf = uf;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        this.cep = cep;
    }

    /** Novo logradouro com os dados deste endereço. */
    public Logradouro paraEntidade() {
        return Logradouro.builder()
                .logradouro(logradouro)
                .numero(numero)
                .complemento(complemento)
                .bairro(bairro)
                .municipio(municipio)
                .uf(uf)
                .cep(cep)
                .build();
    }

    public static LogradouroDTO de(Logradouro entidade) {
        if (entidade == null) return null;
        LogradouroDTO dto = new LogradouroDTO();
        dto.setLogradouro(entidade.getLogradouro());
        dto.setNumero(entidade.getNumero());
        dto.setComplemento(entidade.getComplemento());
        dto.setBairro(entidade.getBairro());
        dto.setMunicipio(entidade.getMunicipio());
        dto.setUf(entidade.getUf());
        dto.setCep(entidade.getCep());
        return dto;
    }
}
