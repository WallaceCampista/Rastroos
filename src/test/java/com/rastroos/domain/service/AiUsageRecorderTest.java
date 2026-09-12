package com.rastroos.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rastroos.domain.entity.AiUsageLog;
import com.rastroos.domain.entity.enums.AiFeature;
import com.rastroos.domain.repository.AiUsageRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Livro-caixa de tokens: base do teto diário e da leitura de custo. */
@ExtendWith(MockitoExtension.class)
class AiUsageRecorderTest {

    @Mock private AiUsageRepository repository;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-12T03:00:00Z"), ZoneOffset.UTC);
    private final UUID alice = UUID.randomUUID();

    private MeterRegistry meters;
    private AiUsageRecorder recorder;

    @BeforeEach
    void setUp() {
        meters = new SimpleMeterRegistry();
        recorder = new AiUsageRecorder(repository, meters, clock);
    }

    @Test
    void registraOConsumoComODiaUtc() {
        recorder.record(alice, AiFeature.CHAT, "gpt-4o-mini", new AiTokenUsage(100, 40, 140));

        ArgumentCaptor<AiUsageLog> saved = ArgumentCaptor.forClass(AiUsageLog.class);
        org.mockito.Mockito.verify(repository).save(saved.capture());
        AiUsageLog entry = saved.getValue();
        assertThat(entry.getUserId()).isEqualTo(alice);
        assertThat(entry.getFeature()).isEqualTo(AiFeature.CHAT);
        assertThat(entry.getModel()).isEqualTo("gpt-4o-mini");
        assertThat(entry.getTotalTokens()).isEqualTo(140);
        assertThat(entry.getUsageDay()).isEqualTo(LocalDate.of(2026, 9, 12));
    }

    @Test
    void publicaMetricasSeparadasPorFuncionalidadeEModelo() {
        recorder.record(alice, AiFeature.INSIGHT, "gpt-4o-mini", new AiTokenUsage(10, 5, 15));

        assertThat(meters.counter("rastroos.ai.calls", "feature", "insight",
                "model", "gpt-4o-mini").count()).isEqualTo(1.0);
        assertThat(meters.counter("rastroos.ai.tokens", "feature", "insight",
                "model", "gpt-4o-mini", "kind", "prompt").count()).isEqualTo(10.0);
        assertThat(meters.counter("rastroos.ai.tokens", "feature", "insight",
                "model", "gpt-4o-mini", "kind", "completion").count()).isEqualTo(5.0);
    }

    @Test
    void falhaAoRegistrarNaoDerrubaARespostaAoUsuario() {
        when(repository.save(any(AiUsageLog.class)))
                .thenThrow(new IllegalStateException("banco fora"));

        assertThatCode(() -> recorder.record(alice, AiFeature.CHAT, "m", AiTokenUsage.ZERO))
                .doesNotThrowAnyException();
    }

    @Test
    void tokensSomamComoEsperado() {
        AiTokenUsage soma = new AiTokenUsage(1, 2, 3).plus(new AiTokenUsage(10, 20, 30));

        assertThat(soma).isEqualTo(new AiTokenUsage(11, 22, 33));
        assertThat(AiTokenUsage.ZERO.plus(soma)).isEqualTo(soma);
    }
}
