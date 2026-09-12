package com.rastroos.domain.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.domain.entity.AiInsight;
import com.rastroos.domain.repository.AiInsightRepository;

/**
 * Persistência dos resumos já gerados, isolada do {@link ScreenInsightService}.
 *
 * <p><strong>Por que é um bean separado</strong>: o service que orquestra a
 * geração não pode ser transacional (a chamada HTTP à IA acontece no meio
 * dele), e {@code @Transactional} num método privado do próprio bean seria
 * ignorado — chamada interna não passa pelo proxy do Spring. Aqui as duas
 * operações são transações curtas de verdade.
 */
@Service
public class InsightStore {

    private static final Logger log = LoggerFactory.getLogger(InsightStore.class);

    private final AiInsightRepository repository;
    private final Clock clock;

    public InsightStore(AiInsightRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Optional<AiInsight> find(UUID userId, String screenKey, String periodKey) {
        return repository.findByUserIdAndScreenAndPeriod(userId, screenKey, periodKey);
    }

    /**
     * Grava o resumo em transação própria, para não depender do que estiver
     * acontecendo em volta.
     *
     * <p>Nunca propaga erro: não conseguir guardar custa, no máximo, uma
     * regeração futura — a tela já está com o texto na mão. Uma corrida entre
     * duas gerações cai na chave única e é tratada da mesma forma.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(UUID userId, String screenKey, String periodKey, String hash,
                     int promptVersion, String model, String text, boolean aiGenerated,
                     long dataVersion) {
        try {
            AiInsight entry = repository.findByUserIdAndScreenAndPeriod(userId, screenKey, periodKey)
                    .orElseGet(AiInsight::new);
            entry.setUserId(userId);
            entry.setScreen(screenKey);
            entry.setPeriod(periodKey);
            entry.setFactsHash(hash);
            entry.setPromptVersion(promptVersion);
            entry.setModel(model);
            entry.setSummaryText(text);
            entry.setAiGenerated(aiGenerated);
            entry.setDataVersion(dataVersion);
            entry.setGeneratedAt(Instant.now(clock));
            repository.save(entry);
        } catch (RuntimeException e) {
            log.warn("Não consegui persistir o resumo de {}: {}", screenKey, e.toString());
        }
    }
}
