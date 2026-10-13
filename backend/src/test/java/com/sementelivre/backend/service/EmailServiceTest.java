package com.sementelivre.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private final CopiaDoPedidoEmail copia =
            new CopiaDoPedidoEmail("ana@exemplo.com", "Assunto", "Texto");

    @Test
    void naoDeveEnviarSemUsuarioOuSenhaDoSmtp() {
        EmailService semSenha = new EmailService(mailSender, "loja@exemplo.com", "", "");
        EmailService semUsuario = new EmailService(mailSender, "", "segredo", "");

        semSenha.enviarCopiaDoPedido(copia);
        semUsuario.enviarEmailRecuperacaoSenha("ana@exemplo.com", "token");

        assertFalse(semSenha.configurado());
        assertFalse(semUsuario.configurado());
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void deveEnviarCopiaDoPedidoComOUsuarioComoRemetentePadrao() {
        EmailService service = new EmailService(mailSender, "loja@exemplo.com", "segredo", "");

        service.enviarCopiaDoPedido(copia);

        SimpleMailMessage enviada = mensagemEnviada();
        assertTrue(service.configurado());
        assertEquals("loja@exemplo.com", enviada.getFrom());
        assertEquals("ana@exemplo.com", enviada.getTo()[0]);
        assertEquals("Assunto", enviada.getSubject());
        assertEquals("Texto", enviada.getText());
    }

    @Test
    void deveUsarORemetenteConfigurado() {
        EmailService service = new EmailService(
                mailSender, "loja@exemplo.com", "segredo", "Semente Livre <nao-responda@exemplo.com>");

        service.enviarCopiaDoPedido(copia);

        assertEquals("Semente Livre <nao-responda@exemplo.com>", mensagemEnviada().getFrom());
    }

    @Test
    void falhaNoSmtpNaoDeveLancarExcecao() {
        EmailService service = new EmailService(mailSender, "loja@exemplo.com", "segredo", "");
        doThrow(new MailSendException("SMTP fora do ar")).when(mailSender).send(any(SimpleMailMessage.class));

        service.enviarCopiaDoPedido(copia);

        verify(mailSender).send(any(SimpleMailMessage.class));
    }

    private SimpleMailMessage mensagemEnviada() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        return captor.getValue();
    }
}
