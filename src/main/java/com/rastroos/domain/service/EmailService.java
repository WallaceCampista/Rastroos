package com.rastroos.domain.service;

/**
 * Contrato de envio de email. Implementação default ({@link LoggingEmailService})
 * apenas loga — em prod, trocar por SMTP/SES/Mailgun. NUNCA registrar o código
 * cru no log de prod; este projeto só faz isso porque a IA é stub em dev.
 */
public interface EmailService {
    void sendVerificationCode(String toEmail, String code);
    void sendPasswordResetCode(String toEmail, String code);
}
