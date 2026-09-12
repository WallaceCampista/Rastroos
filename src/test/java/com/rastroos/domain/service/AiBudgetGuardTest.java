package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.enums.AiFeature;
import com.rastroos.domain.repository.AiUsageRepository;

/**
 * Teto diário de IA. É a última barreira entre um laço acidental e uma fatura
 * inesperada, então cada limite é verificado explicitamente.
 */
@ExtendWith(MockitoExtension.class)
class AiBudgetGuardTest {

    @Mock private AiUsageRepository usage;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T03:00:00Z"), ZoneOffset.UTC);
    private final LocalDate today = LocalDate.of(2026, 9, 12);
    private final UUID alice = UUID.randomUUID();

    private AiProperties props;
    private AiBudgetGuard guard;

    @BeforeEach
    void setUp() {
        props = new AiProperties();
        guard = new AiBudgetGuard(usage, props, clock);
    }

    @Test
    void abaixoDoTeto_deixaPassar() {
        when(usage.dailyTotals(alice, today)).thenReturn(new Object[] {10L, 5_000L});

        assertThatCode(() -> guard.check(alice, AiFeature.CHAT)).doesNotThrowAnyException();
    }

    @Test
    void noLimiteDeChamadas_recusa() {
        props.getBudget().setDailyCallsPerUser(200);
        when(usage.dailyTotals(alice, today)).thenReturn(new Object[] {200L, 1_000L});

        assertThatThrownBy(() -> guard.check(alice, AiFeature.CHAT))
                .isInstanceOf(AiBudgetExceededException.class)
                .isInstanceOf(AiUnavailableException.class);
    }

    @Test
    void noLimiteDeTokens_recusa() {
        props.getBudget().setDailyTokensPerUser(300_000);
        when(usage.dailyTotals(alice, today)).thenReturn(new Object[] {3L, 300_000L});

        assertThatThrownBy(() -> guard.check(alice, AiFeature.EMBEDDING))
                .isInstanceOf(AiBudgetExceededException.class);
    }

    @Test
    void tetoDesligado_naoConsultaNada() {
        props.getBudget().setEnabled(false);

        guard.check(alice, AiFeature.CHAT);

        verifyNoInteractions(usage);
    }

    @Test
    void semDono_naoContabiliza() {
        guard.check(null, AiFeature.INSIGHT);

        verifyNoInteractions(usage);
    }

    @Test
    void primeiroUsoDoDia_semLinhasNoLivroCaixa_deixaPassar() {
        when(usage.dailyTotals(eq(alice), any())).thenReturn(new Object[] {0L, 0L});

        assertThatCode(() -> guard.check(alice, AiFeature.VISION)).doesNotThrowAnyException();
    }

    @Test
    void projecaoAninhadaDoJpa_ehNormalizada() {
        // Conforme o provider, a projeção pode vir como Object[][]; se a
        // normalização falhasse, o teto passaria a contar sempre zero.
        when(usage.dailyTotals(alice, today))
                .thenReturn(new Object[] {new Object[] {500L, 10L}});

        assertThatThrownBy(() -> guard.check(alice, AiFeature.CHAT))
                .isInstanceOf(AiBudgetExceededException.class);
    }

    @Test
    void oDiaUsadoEhOUtc_naoOFusoLocal() {
        // 03:00Z de 12/09 já é dia 12 em UTC, embora ainda seja 11 em Brasília.
        when(usage.dailyTotals(alice, LocalDate.of(2026, 9, 12)))
                .thenReturn(new Object[] {1L, 1L});

        assertThatCode(() -> guard.check(alice, AiFeature.CHAT)).doesNotThrowAnyException();
        assertThat(today).isEqualTo(LocalDate.of(2026, 9, 12));
    }
}
