package com.rastroos.domain.service;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.VerificationCode;
import com.rastroos.domain.entity.enums.VerificationPurpose;
import com.rastroos.domain.repository.VerificationCodeRepository;

/**
 * Cria, valida e descarta códigos de verificação (email/reset).
 * Códigos são números de 6 dígitos. NUNCA são persistidos crus — só o
 * hash SHA-256. Expiração default: 10 minutos.
 */
@Service
public class VerificationCodeService {

    private final VerificationCodeRepository codes;
    private final SecureRandom rng = new SecureRandom();

    @Value("${rastroos.security.verification.ttl-minutes:10}")
    private int ttlMinutes;

    public VerificationCodeService(VerificationCodeRepository codes) {
        this.codes = codes;
    }

    /** Gera código novo, invalida códigos anteriores do mesmo propósito,
     *  persiste hash e retorna o código em claro (apenas para enviar por email). */
    @Transactional
    public String issue(UUID userId, VerificationPurpose purpose) {
        codes.deleteAllForUserAndPurpose(userId, purpose);
        String plain = String.format("%06d", rng.nextInt(1_000_000));
        VerificationCode vc = new VerificationCode();
        vc.setUserId(userId);
        vc.setPurpose(purpose);
        vc.setCodeHash(hash(plain));
        vc.setExpiresAt(Instant.now().plus(Duration.ofMinutes(ttlMinutes)));
        codes.save(vc);
        return plain;
    }

    /** Compara código submetido com o último hash não-usado válido.
     *  Em caso de match, marca como usado. Retorna true se autorizado. */
    @Transactional
    public boolean verifyAndConsume(UUID userId, VerificationPurpose purpose, String code) {
        if (code == null || code.isBlank()) return false;
        Optional<VerificationCode> latest = codes
                .findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByExpiresAtDesc(userId, purpose);
        if (latest.isEmpty()) return false;
        VerificationCode vc = latest.get();
        if (vc.getExpiresAt().isBefore(Instant.now())) return false;
        if (!constantTimeEquals(vc.getCodeHash(), hash(code))) return false;
        vc.setUsedAt(Instant.now());
        codes.save(vc);
        return true;
    }

    static String hash(String code) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(code.getBytes()));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 missing", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }
}
