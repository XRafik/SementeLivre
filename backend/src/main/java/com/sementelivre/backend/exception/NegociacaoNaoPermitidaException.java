package com.sementelivre.backend.exception;

public class NegociacaoNaoPermitidaException extends RuntimeException {

    public NegociacaoNaoPermitidaException(String message) {
        super(message);
    }
}
