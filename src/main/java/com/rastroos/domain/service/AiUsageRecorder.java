package com.rastroos.domain.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.AiUsageLog;
import com.rastroos.domain.entity.enums.AiFeature;
import com.rastroos.domain.repository.AiUsageRepository;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Livro-caixa de tokens: registra o custo de cada chamada ao provedor.
 *
 * <p>Grava em <strong>transação própria</strong>
 * ({@link Propagation#REQUIRES_NEW}): os tokens foram gastos de fato, então o
 * registro não pode desaparecer junto com um rollback do fluxo que o chamou —
 * senão o teto diário passaria a proteger menos do que deveria.
 *
 * <p>Falhar ao registrar nunca derruba a resposta ao usuário: o erro é logado
 * e o fluxo segue.
 */
@Service
public class AiUsageRecorder {

    private static final Logger log = LoggerFactory.getLogger(AiUsageRecorder.class);

    private final AiUsageRepository repository;
    private final MeterRegistry meters;
    private final Clock clock;

    public AiUsageRecorder(AiUsageRepository repository, MeterRegistry meters, Clock clock) {
        this.repository = repository;
        this.meters = meters;
        this.clock = clock;
    }

    /** Persiste o consumo e publica as métricas correspondentes. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID userId, AiFeature feature, String model, AiTokenUsage usage) {
        try {
            AiUsageLog entry = new AiUsageLog();
            entry.setUserId(userId);
            entry.setFeature(feature);
            entry.setModel(model);
            entry.setPromptTokens(usage.promptTokens());
            entry.setCompletionTokens(usage.completionTokens());
            entry.setTotalTokens(usage.totalTokens());
            entry.setUsageDay(today());
            repository.save(entry);

            String tag = feature.name().toLowerCase();
            meters.counter("rastroos.ai.calls", "feature", tag, "model", model).increment();
            meters.counter("rastroos.ai.tokens", "feature", tag, "model", model, "kind", "prompt")
                    .increment(usage.promptTokens());
            meters.counter("rastroos.ai.tokens", "feature", tag, "model", model, "kind", "completion")
                    .increment(usage.completionTokens());
        } catch (RuntimeException e) {
            // Contabilidade é acessória à resposta: registra a falha e segue.
            log.warn("Não consegui registrar o consumo de IA ({}): {}", feature, e.toString());
        }
    }

    /** Dia UTC — o mesmo usado pelo teto diário. */
    LocalDate today() {
        return LocalDate.now(clock.withZone(ZoneOffset.UTC));
    }
}
