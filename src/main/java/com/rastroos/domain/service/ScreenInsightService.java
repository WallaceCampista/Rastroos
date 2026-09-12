package com.rastroos.domain.service;

import java.time.YearMonth;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.AiInsight;
import com.rastroos.web.dto.InsightDto;
import com.rastroos.web.dto.InsightFacts;
import com.rastroos.web.dto.InsightScreen;

/**
 * Resumo do Alfredo para uma tela.
 *
 * <p><strong>Regra de consumo</strong>: o texto é gerado uma única vez por
 * combinação de (usuário, tela, período, <em>dados</em>) e fica no banco. Numa
 * visita seguinte, se a impressão digital dos números for a mesma, o texto sai
 * de {@code ai_insights} <em>sem nenhuma chamada ao provedor</em> — não importa
 * se passaram 15 minutos ou 6 meses. Quem passa um mês sem lançar nada não
 * gera consumo algum, por mais que navegue.
 *
 * <p>O que dispara uma nova geração é só isto: mudança nos números (hash
 * diferente), mudança de modelo ou de versão do prompt. O pré-aquecimento
 * ({@link InsightWarmupService}) cuida disso em segundo plano, de modo que o
 * page load normalmente encontra tudo pronto.
 *
 * <p>Deliberadamente <strong>sem {@code @Transactional}</strong> no método de
 * leitura: os services de dados abrem a própria transação e a chamada HTTP
 * acontece fora delas — anotar aqui seguraria uma conexão do pool durante a
 * espera pela IA.
 */
@Service
public class ScreenInsightService {

    private static final Logger log = LoggerFactory.getLogger(ScreenInsightService.class);

    /** Texto exibido ao acessor quando o titular ocultou os valores. */
    private static final String MASKED_TEXT =
            "O titular optou por ocultar os valores, então não consigo comentar os números desta tela. "
            + "Posso ajudar com organização, prazos e prioridades — é só perguntar.";

    private final InsightFactsBuilder factsBuilder;
    private final AlfredoAiClient ai;
    private final InsightStore store;
    private final UserDataVersionService versions;
    private final AiModelClient model;
    private final AiProperties props;

    /**
     * Chaves sendo geradas agora. Duas abas abrindo a mesma tela ao mesmo tempo
     * não podem virar duas chamadas pagas: a segunda recebe o texto local e a
     * primeira persiste o da IA.
     */
    private final Map<String, Boolean> inFlight = new ConcurrentHashMap<>();

    public ScreenInsightService(InsightFactsBuilder factsBuilder,
                                AlfredoAiClient ai,
                                InsightStore store,
                                UserDataVersionService versions,
                                AiModelClient model,
                                AiProperties props) {
        this.factsBuilder = factsBuilder;
        this.ai = ai;
        this.store = store;
        this.versions = versions;
        this.model = model;
        this.props = props;
    }

    /** Resumo da tela para o período pedido. Nunca lança por causa da IA. */
    public InsightDto insight(UUID userId, InsightScreen screen, YearMonth period) {
        String periodKey = periodKey(screen, period);
        InsightFacts facts = factsBuilder.build(userId, screen, period);
        String hash = facts.fingerprint();

        Optional<AiInsight> cached = store.find(userId, screen.key(), periodKey);
        if (cached.isPresent() && isReusable(cached.get(), hash)) {
            AiInsight hit = cached.get();
            return new InsightDto(screen.key(), screen.label(), displayPeriod(screen, period),
                    hit.getSummaryText(), hit.isAiGenerated());
        }

        return generate(userId, screen, period, facts, hash);
    }

    /**
     * Gera (e persiste) o resumo. Usado pelo pré-aquecimento e pelo raro caso
     * de tela nunca vista antes.
     */
    public InsightDto generate(UUID userId, InsightScreen screen, YearMonth period) {
        InsightFacts facts = factsBuilder.build(userId, screen, period);
        return generate(userId, screen, period, facts, facts.fingerprint());
    }

    private InsightDto generate(UUID userId, InsightScreen screen, YearMonth period,
                                InsightFacts facts, String hash) {
        String periodKey = periodKey(screen, period);
        String lockKey = userId + "|" + screen.key() + "|" + periodKey;

        String text = facts.fallbackText();
        boolean aiGenerated = false;

        if (inFlight.putIfAbsent(lockKey, Boolean.TRUE) == null) {
            try {
                Optional<String> written = ai.summarize(userId, prompt(facts));
                if (written.isPresent() && !written.get().isBlank()) {
                    text = written.get().strip();
                    aiGenerated = true;
                }
            } catch (RuntimeException e) {
                // Resumo é acessório: qualquer falha cai no texto local, a tela não quebra.
                log.warn("Falha ao gerar resumo da tela {} ({}), usando o resumo local",
                        screen.key(), e.toString());
            } finally {
                inFlight.remove(lockKey);
            }
            store.save(userId, screen.key(), periodKey, hash,
                    props.getInsight().getPromptVersion(), model.chatModel(),
                    text, aiGenerated, versions.currentVersion(userId));
        } else {
            log.debug("Resumo de {} já está sendo gerado; devolvendo o texto local", screen.key());
        }

        return new InsightDto(screen.key(), screen.label(), displayPeriod(screen, period),
                text, aiGenerated);
    }

    /** Resumo neutro (sem números) para acessor com valores mascarados. */
    public InsightDto maskedInsight(InsightScreen screen, YearMonth period) {
        return new InsightDto(screen.key(), screen.label(),
                displayPeriod(screen, period), MASKED_TEXT, false);
    }

    /**
     * Só vale reaproveitar quando os números, o modelo e a versão do prompt são
     * os mesmos. Um resumo gerado pelo texto local ({@code aiGenerated=false})
     * também é guardado — assim uma indisponibilidade momentânea não faz a tela
     * insistir numa chamada a cada visita; o pré-aquecimento o substitui quando
     * o provedor voltar.
     */
    private boolean isReusable(AiInsight hit, String hash) {
        return hit.getFactsHash().equals(hash)
                && hit.getPromptVersion() == props.getInsight().getPromptVersion()
                && hit.getModel().equals(model.chatModel());
    }

    // ── Prompt ───────────────────────────────────────────────────────────

    /**
     * Prompt enviado à IA: os números prontos + o resumo local como referência
     * de tom. A instrução de não inventar valores está no prompt de sistema do
     * {@link AlfredoAiClient}.
     */
    static String prompt(InsightFacts facts) {
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

    /** Chave de linha: telas sem mês usam o sentinela, nunca {@code null}. */
    private static String periodKey(InsightScreen screen, YearMonth period) {
        return screen.isPeriodic() ? period.toString() : AiInsight.NO_PERIOD;
    }

    /** Período exibido ao cliente: {@code null} nas telas sem mês. */
    private static String displayPeriod(InsightScreen screen, YearMonth period) {
        return screen.isPeriodic() ? period.toString() : null;
    }
}
