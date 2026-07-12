package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * O stub de email só escreve no log. Garante que os dois envios não lançam
 * (contrato mínimo do {@link EmailService} em dev/test).
 */
class LoggingEmailServiceTest {

    private final LoggingEmailService service = new LoggingEmailService();

    @Test
    void enviosNaoLancam() {
        assertThatCode(() -> {
            service.sendVerificationCode("user@example.com", "123456");
            service.sendPasswordResetCode("user@example.com", "654321");
        }).doesNotThrowAnyException();
    }
}
