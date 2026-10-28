package com.sementelivre.backend.exception;

import java.time.Duration;

/** Acima de um limite de tentativas: a API responde 429 com Retry-After. */
public class MuitasTentativasException extends RuntimeException {

    private final Duration tentarDeNovoEm;

    public MuitasTentativasException(String message, Duration tentarDeNovoEm) {
        super(message);
        this.tentarDeNovoEm = tentarDeNovoEm;
    }

    public Duration getTentarDeNovoEm() {
        return tentarDeNovoEm;
    }
}
