package com.rastroos.domain.service;

import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.rastroos.config.AlfredoProperties;
import com.rastroos.web.dto.InsightDto;
import com.rastroos.web.dto.InsightFacts;
import com.rastroos.web.dto.InsightScreen;

/**
 * Resumo do Alfredo para uma tela: lê os números da tela
 * ({@link InsightFactsBuilder}), pede à IA que os reescreva e devolve o texto
 * do balão flutuante. Sem IA configurada — ou com o provedor fora do ar — cai
 * no resumo determinístico, que já sai pronto do builder.
 *
 * <p>Deliberadamente <strong>sem {@code @Transactional}</strong>: os services
 * de leitura abrem a própria transação, e a chamada HTTP à IA acontece depois
 * delas. Anotar aqui seguraria uma conexão do pool durante a chamada externa.
 *
 * <p>O resultado é cacheado por (usuário, tela, período, <em>impressão digital
 * dos números</em>). Como a chave inclui os próprios valores, qualquer
 * lançamento novo invalida o resumo na hora; o TTL só existe para expulsar
 * entradas antigas.
 */
@Service
public class ScreenInsightService {

    private static final Logger log = LoggerFactory.getLogger(ScreenInsightService.class);

    /** Teto de entradas em memória; ao estourar, expira o que já venceu. */
    private static final int MAX_CACHE_ENTRIES = 1_000;

    /** Texto exibido ao acessor quando o titular ocultou os valores. */
    private static final String MASKED_TEXT =
            "O titular optou por ocultar os valores, então não consigo comentar os números desta tela. "
            + "Posso ajudar com organização, prazos e prioridades — é só perguntar.";

    private final InsightFactsBuilder factsBuilder;
    private final AlfredoAiClient ai;
    private final AlfredoProperties props;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ScreenInsightService(InsightFactsBuilder factsBuilder,
                                AlfredoAiClient ai,
                                AlfredoProperties props) {
        this.factsBuilder = factsBuilder;
        this.ai = ai;
        this.props = props;
    }

    private record CacheEntry(String text, boolean aiGenerated, Instant expiresAt) {
        boolean isFresh(Instant now) {
            return now.isBefore(expiresAt);
        }
    }

    /** Resumo da tela para o período pedido. Nunca lança por causa da IA. */
    public InsightDto insight(UUID userId, InsightScreen screen, YearMonth period) {
        InsightFacts facts = factsBuilder.build(userId, screen, period);
        String periodKey = screen.isPeriodic() ? period.toString() : null;

        String key = cacheKey(userId, screen, periodKey, facts);
        CacheEntry cached = cache.get(key);
        Instant now = Instant.now();
        if (cached != null && cached.isFresh(now)) {
            return new InsightDto(screen.key(), screen.label(), periodKey,
                    cached.text(), cached.aiGenerated());
        }

        String text = facts.fallbackText();
        boolean aiGenerated = false;
        try {
            Optional<String> written = ai.summarize(prompt(facts));
            if (written.isPresent() && !written.get().isBlank()) {
                text = written.get().strip();
                aiGenerated = true;
            }
        } catch (RuntimeException e) {
            // Resumo é acessório: qualquer falha cai no texto local, a tela não quebra.
            log.warn("Falha ao gerar resumo da tela {} ({}), usando o resumo local",
                    screen.key(), e.toString());
        }

        store(key, new CacheEntry(text, aiGenerated,
                now.plus(Duration.ofSeconds(Math.max(1, props.getInsightCacheTtlSeconds())))));
        return new InsightDto(screen.key(), screen.label(), periodKey, text, aiGenerated);
    }

    /** Resumo neutro (sem números) para acessor com valores mascarados. */
    public InsightDto maskedInsight(InsightScreen screen, YearMonth period) {
        return new InsightDto(screen.key(), screen.label(),
                screen.isPeriodic() ? period.toString() : null, MASKED_TEXT, false);
    }

    /**
     * Prompt enviado à IA: os números prontos + o resumo local como referência
     * de tom. A instrução de não inventar valores está no system prompt
     * ({@code alfredo.insight-system-prompt}).
     */
    private static String prompt(InsightFacts facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tela: ").append(facts.screen().label());
        if (facts.periodLabel() != null) {
            sb.append(" — período: ").append(facts.periodLabel());
        }
        sb.append("\n\nDados desta tela:\n");
        for (String line : facts.lines()) {
            sb.append("- ").append(line).append('\n');
        }
        sb.append("\nResumo automático de referência (mesmos números, pode reescrever):\n")
          .append(facts.fallbackText())
          .append("\n\nEscreva o resumo final para a pessoa.");
        return sb.toString();
    }

    private static String cacheKey(UUID userId, InsightScreen screen, String periodKey,
                                   InsightFacts facts) {
        return userId + "|" + screen.key() + "|" + periodKey + "|" + facts.fallbackText().hashCode();
    }

    private void store(String key, CacheEntry entry) {
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            evictExpired();
        }
        cache.put(key, entry);
    }

    /** Remove o que já venceu; se ainda estiver cheio, zera (cache é descartável). */
    private void evictExpired() {
        Instant now = Instant.now();
        Iterator<Map.Entry<String, CacheEntry>> it = cache.entrySet().iterator();
        while (it.hasNext()) {
            if (!it.next().getValue().isFresh(now)) {
                it.remove();
            }
        }
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
    }
}
