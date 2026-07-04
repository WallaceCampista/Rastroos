package com.rastroos.domain.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Implementação stub que só escreve no log. Útil em dev/test. Em prod,
 * defina um bean {@link EmailService} concreto (SMTP/SES) e este aqui some.
 */
@Service
@ConditionalOnMissingBean(name = "smtpEmailService")
@Profile({"dev", "test"})
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        log.info("[EMAIL][dev-stub] verify code para {} = {}", toEmail, code);
    }

    @Override
    public void sendPasswordResetCode(String toEmail, String code) {
        log.info("[EMAIL][dev-stub] reset code para {} = {}", toEmail, code);
    }
}
