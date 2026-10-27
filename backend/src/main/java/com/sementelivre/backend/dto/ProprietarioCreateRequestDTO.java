package com.sementelivre.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ProprietarioCreateRequestDTO extends UsuarioCreateRequestDTO {

    @NotBlank(message = "O RG é obrigatório para proprietário")
    @Size(max = 20, message = "O RG deve ter no máximo 20 caracteres")
    private String rg;

    private Boolean exibirNoSitePublico = false;

    public String getRg() {
        return rg;
    }

    public void setRg(String rg) {
        this.rg = rg;
    }

    public Boolean getExibirNoSitePublico() {
        return exibirNoSitePublico;
    }

    public void setExibirNoSitePublico(Boolean exibirNoSitePublico) {
        this.exibirNoSitePublico = exibirNoSitePublico;
    }
}
