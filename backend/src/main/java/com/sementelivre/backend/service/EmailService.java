package com.sementelivre.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final String usuario;
    private final String senha;
    private final String remetente;

    // As credenciais vêm de MAIL_USERNAME / MAIL_PASSWORD (ver backend/.env.example)
    public EmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String usuario,
            @Value("${spring.mail.password:}") String senha,
            @Value("${app.mail.from:}") String remetente) {

        this.mailSender = mailSender;
        this.usuario = usuario;
        this.senha = senha;
        this.remetente = remetente.isBlank() ? usuario : remetente;
    }

    /** Sem usuário e senha do SMTP nenhum e-mail é enviado. */
    public boolean configurado() {
        return !usuario.isBlank() && !senha.isBlank();
    }

    public void enviarEmailRecuperacaoSenha(String para, String token) {
        enviar(para,
                "Semente Livre - Recuperação de Senha",
                "Você solicitou a redefinição de senha.\n\n" +
                        "Utilize o seguinte token no aplicativo para redefinir sua senha:\n" +
                        token + "\n\n" +
                        "Este token expira em 15 minutos.");
    }

    public void enviarCopiaDoPedido(CopiaDoPedidoEmail copia) {
        enviar(copia.para(), copia.assunto(), copia.texto());
    }

    private void enviar(String para, String assunto, String texto) {

        if (!configurado()) {
            log.info("E-mail \"{}\" não enviado: MAIL_USERNAME/MAIL_PASSWORD não configurados", assunto);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(remetente);
            message.setTo(para);
            message.setSubject(assunto);
            message.setText(texto);
            mailSender.send(message);
        } catch (Exception e) {
            // Falha no SMTP não pode derrubar o fluxo que pediu o envio
            log.warn("Falha ao enviar o e-mail \"{}\": {}", assunto, e.getMessage());
        }
    }
}
