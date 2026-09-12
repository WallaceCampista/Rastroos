package com.rastroos.domain.service;

import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.AiUserState;
import com.rastroos.domain.repository.AiUserStateRepository;
import com.rastroos.web.dto.InsightScreen;

/**
 * Pré-aquecimento: quando o usuário grava algo, regera <strong>de uma vez</strong>
 * os resumos de todas as telas afetadas e atualiza o índice semântico, em
 * segundo plano.
 *
 * <p>É o que tira a IA do caminho do page load. O balão do Alfredo passa a ler
 * um texto já pronto no banco; a chamada ao provedor acontece aqui, uma vez por
 * mudança de dado — não uma vez por visita.
 *
 * <p>Duas proteções contra desperdício:
 * <ul>
 *   <li><strong>Debounce</strong>: a varredura só pega quem parou de escrever
 *       há {@code ai.warmup.debounce-seconds}. Dez lançamentos seguidos viram
 *       uma geração, não dez.</li>
 *   <li><strong>Impressão digital</strong>: cada tela passa pelo mesmo
 *       {@link ScreenInsightService#insight} da leitura normal, que só chama a
 *       IA se os números daquela tela mudaram de fato. Lançar um gasto não
 *       regera o resumo de Receitas.</li>
 * </ul>
 */
@Service
public class InsightWarmupService {

    private static final Logger log = LoggerFactory.getLogger(InsightWarmupService.class);

    private final AiUserStateRepository states;
    private final UserDataVersionService versions;
    private final ScreenInsightService insights;
    private final VectorIndexService vectors;
    private final AlfredoAiClient ai;
    private final AiProperties props;
    private final java.time.Clock clock;

    public InsightWarmupService(AiUserStateRepository states,
                                UserDataVersionService versions,
                                ScreenInsightService insights,
                                VectorIndexService vectors,
                                AlfredoAiClient ai,
                                AiProperties props,
                                java.time.Clock clock) {
        this.states = states;
        this.versions = versions;
        this.insights = insights;
        this.vectors = vectors;
        this.ai = ai;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Varre os usuários com escrita recente já encerrada e aquece cada um.
     *
     * <p>{@code fixedDelay} (e não {@code fixedRate}): a próxima volta só começa
     * depois que esta termina, então um aquecimento demorado nunca empilha
     * execuções concorrentes.
     */
    @Scheduled(fixedDelayString = "${ai.warmup.sweep-interval-ms:15000}",
               initialDelayString = "${ai.warmup.sweep-interval-ms:15000}")
    public void sweep() {
        AiProperties.Warmup cfg = props.getWarmup();
        if (!cfg.isEnabled()) {
            return;
        }

        Instant threshold = Instant.now(clock).minusSeconds(Math.max(0, cfg.getDebounceSeconds()));
        List<AiUserState> due = states.findDueForWarmup(
                threshold, Limit.of(Math.max(1, cfg.getMaxUsersPerSweep())));
        if (due.isEmpty()) {
            return;
        }

        log.debug("Pré-aquecimento: {} usuário(s) com dados novos", due.size());
        for (AiUserState state : due) {
            warm(state.getUserId(), state.getDataVersion());
        }
    }

    /**
     * Aquece um usuário: índice semântico + resumo de todas as telas nos meses
     * de interesse. Captura a versão no início para que uma escrita ocorrida
     * durante o aquecimento não seja dada como já refletida.
     */
    public void warm(UUID userId, long version) {
        versions.claimForWarmup(userId);
        try {
            if (!ai.isEnabled()) {
                // Sem IA não há o que pré-gerar: o texto local já é instantâneo.
                versions.markWarmed(userId, version);
                return;
            }

            int generated = 0;
            for (YearMonth period : periods()) {
                for (InsightScreen screen : InsightScreen.values()) {
                    // Telas sem mês só precisam de uma passada.
                    if (!screen.isPeriodic() && !period.equals(currentPeriod())) {
                        continue;
                    }
                    generated += warmScreen(userId, screen, period);
                }
            }
            // Índice semântico depois e por fora: ele é um extra do chat, e não
            // pode derrubar os resumos das telas, que funcionam mesmo sem IA.
            int reindexed = reindexQuietly(userId);

            versions.markWarmed(userId, version);
            log.debug("Pré-aquecimento concluído: {} tela(s) tocadas, {} documento(s) vetorizados",
                    generated, reindexed);
        } catch (RuntimeException e) {
            int backoff = props.getWarmup().getFailureBackoffSeconds();
            log.warn("Pré-aquecimento falhou; nova tentativa em {}s: {}", backoff, e.toString());
            versions.markWarmFailed(userId, e.toString(), backoff);
        }
    }

    /** Reindexação best-effort: falha aqui não invalida o aquecimento. */
    private int reindexQuietly(UUID userId) {
        try {
            return vectors.reindex(userId);
        } catch (RuntimeException e) {
            log.debug("Índice semântico não pôde ser atualizado agora ({}); segue sem ele",
                    e.toString());
            return 0;
        }
    }

    /**
     * Passa por uma tela. Reusa o caminho normal de leitura de propósito: ele
     * já compara a impressão digital e só chama a IA se os números mudaram.
     */
    private int warmScreen(UUID userId, InsightScreen screen, YearMonth period) {
        try {
            insights.insight(userId, screen, period);
            return 1;
        } catch (RuntimeException e) {
            log.debug("Pré-aquecimento da tela {} falhou ({}); segue para a próxima",
                    screen.key(), e.toString());
            return 0;
        }
    }

    /** Mês corrente mais os anteriores configurados, do mais novo ao mais antigo. */
    private List<YearMonth> periods() {
        YearMonth current = currentPeriod();
        int back = Math.max(0, props.getWarmup().getMonthsBack());
        List<YearMonth> out = new ArrayList<>(back + 1);
        for (int i = 0; i <= back; i++) {
            out.add(current.minusMonths(i));
        }
        return out;
    }

    private YearMonth currentPeriod() {
        return YearMonth.now(clock);
    }
}
