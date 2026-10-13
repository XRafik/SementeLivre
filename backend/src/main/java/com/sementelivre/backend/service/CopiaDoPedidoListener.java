package com.sementelivre.backend.service;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Envia a cópia do pedido só depois do commit e fora da thread da requisição:
 * um pedido que deu rollback não gera e-mail, e um SMTP lento ou fora do ar
 * não atrasa nem desfaz o registro do pedido.
 */
@Component
public class CopiaDoPedidoListener {

    private final EmailService emailService;

    public CopiaDoPedidoListener(EmailService emailService) {
        this.emailService = emailService;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void enviar(CopiaDoPedidoEmail copia) {
        emailService.enviarCopiaDoPedido(copia);
    }
}
