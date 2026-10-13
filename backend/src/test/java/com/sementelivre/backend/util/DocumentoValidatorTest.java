package com.sementelivre.backend.util;

import com.sementelivre.backend.entity.enums.TipoDocumento;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class DocumentoValidatorTest {

    @Test
    public void aceitaCpfValido() {
        assertThatCode(() -> DocumentoValidator.validar(TipoDocumento.CPF, "52998224725"))
                .doesNotThrowAnyException();
    }

    @Test
    public void rejeitaCpfComDigitoVerificadorInvalido() {
        assertThatThrownBy(() -> DocumentoValidator.validar(TipoDocumento.CPF, "52998224726"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dígito verificador");
    }

    @Test
    public void rejeitaCpfComTamanhoInvalido() {
        assertThatThrownBy(() -> DocumentoValidator.validar(TipoDocumento.CPF, "123456789"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejeitaCpfComTodosDigitosIguais() {
        assertThatThrownBy(() -> DocumentoValidator.validar(TipoDocumento.CPF, "11111111111"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void aceitaCnpjValido() {
        assertThatCode(() -> DocumentoValidator.validar(TipoDocumento.CNPJ, "11222333000181"))
                .doesNotThrowAnyException();
    }

    @Test
    public void rejeitaCnpjComDigitoVerificadorInvalido() {
        assertThatThrownBy(() -> DocumentoValidator.validar(TipoDocumento.CNPJ, "11222333000180"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dígito verificador");
    }

    @Test
    public void rejeitaDocumentoComCaracteresNaoNumericos() {
        assertThatThrownBy(() -> DocumentoValidator.validar(TipoDocumento.CPF, "5299822472A"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void rejeitaTipoDocumentoNulo() {
        assertThatThrownBy(() -> DocumentoValidator.validar(null, "52998224725"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
