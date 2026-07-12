package com.rastroos.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Fallback de {@link EmailService} para o perfil <b>prod</b> quando nenhum bean
 * concreto de envio (SMTP/SES) foi registrado (nome {@code smtpEmailService}).
 *
 * <p>Diferente do stub de dev, <b>nunca</b> registra o código no log (§5.9): só
 * emite um WARN operacional avisando que a entrega de e-mail não está
 * configurada. Assim a aplicação <em>sobe</em> em prod (verificação/reset ficam
 * inertes) e o operador é claramente sinalizado a plugar um provedor real.
 */
@Service
@Profile("prod")
@ConditionalOnMissingBean(name = "smtpEmailService")
public class UnconfiguredEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(UnconfiguredEmailService.class);

    private static final String MSG =
            "EmailService nao configurado (prod): {} NAO enviado. "
          + "Registre um bean 'smtpEmailService' (SMTP/SES) para habilitar o fluxo.";

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        log.warn(MSG, "codigo de verificacao");
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code) {
        log.warn(MSG, "codigo de reset de senha");
    }
}
