package com.sementelivre.backend.exception;

import java.time.Duration;

public class LimiteDePedidosExcedidoException extends RuntimeException {

    private final Duration tentarDeNovoEm;

    public LimiteDePedidosExcedidoException(String message, Duration tentarDeNovoEm) {
        super(message);
        this.tentarDeNovoEm = tentarDeNovoEm;
    }

    public Duration getTentarDeNovoEm() {
        return tentarDeNovoEm;
    }
}
