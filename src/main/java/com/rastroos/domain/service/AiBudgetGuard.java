package com.rastroos.domain.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.rastroos.config.AiProperties;
import com.rastroos.domain.entity.enums.AiFeature;
import com.rastroos.domain.repository.AiUsageRepository;

/**
 * Teto diário de IA por usuário, medido no livro-caixa {@code ai_usage}.
 *
 * <p>Existe para que nenhum caminho — nem um laço acidental no cliente, nem um
 * usuário curioso apertando "enviar" mil vezes — consiga transformar a
 * credencial num prejuízo. O limite é por dia UTC e vale para todas as
 * funcionalidades somadas.
 *
 * <p>Verificação em transação somente-leitura, antes da chamada. Passar do teto
 * lança {@link AiBudgetExceededException}, que os chamadores traduzem em
 * resposta de contingência.
 */
@Service
public class AiBudgetGuard {

    private static final Logger log = LoggerFactory.getLogger(AiBudgetGuard.class);

    private final AiUsageRepository usage;
    private final AiProperties props;
    private final Clock clock;

    public AiBudgetGuard(AiUsageRepository usage, AiProperties props, Clock clock) {
        this.usage = usage;
        this.props = props;
        this.clock = clock;
    }

    /**
     * Barra a chamada quando o usuário já estourou chamadas ou tokens do dia.
     * {@code userId} nulo (aquecimento administrativo) não é contabilizado.
     */
    @Transactional(readOnly = true)
    public void check(UUID userId, AiFeature feature) {
        AiProperties.Budget budget = props.getBudget();
        if (!budget.isEnabled() || userId == null) {
            return;
        }

        LocalDate day = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        Object[] totals = usage.dailyTotals(userId, day);
        long calls = asLong(totals, 0);
        long tokens = asLong(totals, 1);

        if (calls >= budget.getDailyCallsPerUser()) {
            log.info("Teto diário de chamadas de IA atingido ({} de {}) — recusando {}",
                    calls, budget.getDailyCallsPerUser(), feature);
            throw new AiBudgetExceededException("Limite diário de chamadas de IA atingido");
        }
        if (tokens >= budget.getDailyTokensPerUser()) {
            log.info("Teto diário de tokens de IA atingido ({} de {}) — recusando {}",
                    tokens, budget.getDailyTokensPerUser(), feature);
            throw new AiBudgetExceededException("Limite diário de tokens de IA atingido");
        }
    }

    /**
     * A projeção do JPA devolve {@code Object[]} ou {@code Object[][]} conforme
     * o provider; normaliza as duas formas.
     */
    private static long asLong(Object[] row, int index) {
        Object[] values = row != null && row.length == 1 && row[0] instanceof Object[] nested
                ? nested
                : row;
        if (values == null || values.length <= index || !(values[index] instanceof Number n)) {
            return 0L;
        }
        return n.longValue();
    }
}
