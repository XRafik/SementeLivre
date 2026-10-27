package com.sementelivre.backend.exception;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Tratamento de erro da geracao de relatorios (issue #95).
 *
 * <p>Fica em um advice separado para nao mexer no GlobalExceptionHandler, que e
 * do Dev 1. A resposta usa o mesmo ErrorResponse padronizado do projeto.</p>
 */
@RestControllerAdvice
public class RelatorioExceptionHandler {

    // 500: o pedido estava correto, quem falhou foi a montagem do arquivo
    @ExceptionHandler(RelatorioException.class)
    public ResponseEntity<ErrorResponse> handleRelatorioException(
            RelatorioException e, HttpServletRequest request) {

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        ErrorResponse err = new ErrorResponse(
                "Erro ao gerar relatorio",
                e.getMessage(),
                Instant.now(),
                status.value(),
                request.getRequestURI(),
                null
        );

        return ResponseEntity.status(status).body(err);
    }
}
