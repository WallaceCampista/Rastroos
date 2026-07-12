package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

/**
 * O fallback de prod não lança e (por contrato) não expõe o código —
 * apenas avisa que o envio não está configurado.
 */
class UnconfiguredEmailServiceTest {

    private final UnconfiguredEmailService service = new UnconfiguredEmailService();

    @Test
    void enviosNaoLancam() {
        assertThatCode(() -> {
            service.sendVerificationCode("user@example.com", "123456");
            service.sendPasswordResetCode("user@example.com", "654321");
        }).doesNotThrowAnyException();
    }
}
