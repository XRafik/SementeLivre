package com.sementelivre.backend.exception;

/**
 * Falha ao montar o arquivo do relatorio (PDF ou CSV) - issue #95.
 */
public class RelatorioException extends RuntimeException {

    public RelatorioException(String message, Throwable cause) {
        super(message, cause);
    }
}
