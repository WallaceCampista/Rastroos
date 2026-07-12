package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.rastroos.domain.entity.VerificationCode;
import com.rastroos.domain.entity.enums.VerificationPurpose;
import com.rastroos.domain.repository.VerificationCodeRepository;

@ExtendWith(MockitoExtension.class)
class VerificationCodeServiceTest {

    @Mock private VerificationCodeRepository codes;

    private VerificationCodeService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new VerificationCodeService(codes);
        ReflectionTestUtils.setField(service, "ttlMinutes", 10);
    }

    @Test
    void issueInvalidaAnterioresPersisteHashERetornaCodigoEmClaro() {
        when(codes.save(any(VerificationCode.class))).thenAnswer(inv -> inv.getArgument(0));

        String plain = service.issue(userId, VerificationPurpose.EMAIL_VERIFY);

        assertThat(plain).hasSize(6).containsOnlyDigits();
        verify(codes).deleteAllForUserAndPurpose(userId, VerificationPurpose.EMAIL_VERIFY);

        ArgumentCaptor<VerificationCode> captor = ArgumentCaptor.forClass(VerificationCode.class);
        verify(codes).save(captor.capture());
        VerificationCode saved = captor.getValue();
        assertThat(saved.getCodeHash())
                .isEqualTo(VerificationCodeService.hash(plain))
                .isNotEqualTo(plain);                    // nunca persiste o código cru
        assertThat(saved.getExpiresAt()).isAfter(Instant.now());
        assertThat(saved.getPurpose()).isEqualTo(VerificationPurpose.EMAIL_VERIFY);
    }

    @Test
    void verifyCodigoNuloOuVazioFalha() {
        assertThat(service.verifyAndConsume(userId, VerificationPurpose.EMAIL_VERIFY, null)).isFalse();
        assertThat(service.verifyAndConsume(userId, VerificationPurpose.EMAIL_VERIFY, "  ")).isFalse();
        verify(codes, never()).save(any());
    }

    @Test
    void verifySemCodigoArmazenadoFalha() {
        when(codes.findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByExpiresAtDesc(
                userId, VerificationPurpose.PASSWORD_RESET)).thenReturn(Optional.empty());

        assertThat(service.verifyAndConsume(userId, VerificationPurpose.PASSWORD_RESET, "123456"))
                .isFalse();
    }

    @Test
    void verifyCodigoExpiradoFalha() {
        VerificationCode vc = code("123456", Instant.now().minusSeconds(60));
        when(codes.findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByExpiresAtDesc(
                userId, VerificationPurpose.EMAIL_VERIFY)).thenReturn(Optional.of(vc));

        assertThat(service.verifyAndConsume(userId, VerificationPurpose.EMAIL_VERIFY, "123456"))
                .isFalse();
        verify(codes, never()).save(any());
    }

    @Test
    void verifyHashDivergenteFalha() {
        VerificationCode vc = code("123456", Instant.now().plusSeconds(600));
        when(codes.findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByExpiresAtDesc(
                userId, VerificationPurpose.EMAIL_VERIFY)).thenReturn(Optional.of(vc));

        assertThat(service.verifyAndConsume(userId, VerificationPurpose.EMAIL_VERIFY, "000000"))
                .isFalse();
    }

    @Test
    void verifyFelizMarcaUsadoERetornaTrue() {
        VerificationCode vc = code("123456", Instant.now().plusSeconds(600));
        when(codes.findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByExpiresAtDesc(
                userId, VerificationPurpose.EMAIL_VERIFY)).thenReturn(Optional.of(vc));

        assertThat(service.verifyAndConsume(userId, VerificationPurpose.EMAIL_VERIFY, "123456"))
                .isTrue();
        assertThat(vc.getUsedAt()).isNotNull();
        verify(codes).save(vc);
    }

    private VerificationCode code(String plain, Instant expiresAt) {
        VerificationCode vc = new VerificationCode();
        vc.setUserId(userId);
        vc.setPurpose(VerificationPurpose.EMAIL_VERIFY);
        vc.setCodeHash(VerificationCodeService.hash(plain));
        vc.setExpiresAt(expiresAt);
        return vc;
    }
}
