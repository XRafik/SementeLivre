package com.sementelivre.backend.exception;

import java.time.Duration;

public class LimiteDePedidosExcedidoException extends MuitasTentativasException {

    public LimiteDePedidosExcedidoException(String message, Duration tentarDeNovoEm) {
        super(message, tentarDeNovoEm);
    }
}
