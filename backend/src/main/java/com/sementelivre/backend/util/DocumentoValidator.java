package com.sementelivre.backend.util;

import com.sementelivre.backend.entity.enums.TipoDocumento;

public final class DocumentoValidator {

    private DocumentoValidator() {
    }

    public static void validar(TipoDocumento tipoDocumento, String documento) {
        if (tipoDocumento == null) {
            throw new IllegalArgumentException("O tipo do documento é obrigatório");
        }
        if (documento == null || !documento.matches("\\d+")) {
            throw new IllegalArgumentException("O documento deve conter apenas dígitos");
        }

        switch (tipoDocumento) {
            case CPF -> validarCpf(documento);
            case CNPJ -> validarCnpj(documento);
        }
    }

    private static void validarCpf(String cpf) {
        if (cpf.length() != 11) {
            throw new IllegalArgumentException("CPF deve conter exatamente 11 dígitos");
        }
        if (todosDigitosIguais(cpf)) {
            throw new IllegalArgumentException("CPF inválido");
        }

        int[] d = digitos(cpf);
        int dv1 = calcularDigitoVerificador(d, 9, 10);
        int dv2 = calcularDigitoVerificador(d, 10, 11);

        if (d[9] != dv1 || d[10] != dv2) {
            throw new IllegalArgumentException("CPF inválido: dígito verificador incorreto");
        }
    }

    private static void validarCnpj(String cnpj) {
        if (cnpj.length() != 14) {
            throw new IllegalArgumentException("CNPJ deve conter exatamente 14 dígitos");
        }
        if (todosDigitosIguais(cnpj)) {
            throw new IllegalArgumentException("CNPJ inválido");
        }

        int[] d = digitos(cnpj);
        int dv1 = calcularDigitoVerificadorCnpj(d, 12);
        int dv2 = calcularDigitoVerificadorCnpj(d, 13);

        if (d[12] != dv1 || d[13] != dv2) {
            throw new IllegalArgumentException("CNPJ inválido: dígito verificador incorreto");
        }
    }

    private static int calcularDigitoVerificador(int[] digitos, int quantidade, int pesoInicial) {
        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += digitos[i] * (pesoInicial - i);
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static int calcularDigitoVerificadorCnpj(int[] digitos, int quantidade) {
        int[] pesos = quantidade == 12
                ? new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2}
                : new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

        int soma = 0;
        for (int i = 0; i < quantidade; i++) {
            soma += digitos[i] * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }

    private static boolean todosDigitosIguais(String documento) {
        char primeiro = documento.charAt(0);
        for (int i = 1; i < documento.length(); i++) {
            if (documento.charAt(i) != primeiro) {
                return false;
            }
        }
        return true;
    }

    private static int[] digitos(String documento) {
        int[] resultado = new int[documento.length()];
        for (int i = 0; i < documento.length(); i++) {
            resultado[i] = Character.getNumericValue(documento.charAt(i));
        }
        return resultado;
    }
}
