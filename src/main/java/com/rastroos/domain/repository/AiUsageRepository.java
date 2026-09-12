package com.rastroos.domain.repository;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.AiUsageLog;

/**
 * Leitura do livro-caixa de tokens. Sem regra de negócio — os tetos ficam em
 * {@code AiBudgetGuard}.
 */
public interface AiUsageRepository extends JpaRepository<AiUsageLog, Long> {

    /**
     * Chamadas e tokens do dia numa passada só: {@code [count, totalTokens]}.
     * Uma consulta em vez de duas porque isso roda antes de <em>toda</em>
     * chamada de IA.
     */
    @Query("""
            SELECT COUNT(u), COALESCE(SUM(u.totalTokens), 0) FROM AiUsageLog u
             WHERE u.userId = :userId AND u.usageDay = :day
            """)
    Object[] dailyTotals(@Param("userId") UUID userId, @Param("day") LocalDate day);

    /** Limpeza do histórico antigo (o livro-caixa não precisa crescer para sempre). */
    long deleteByUsageDayBefore(LocalDate cutoff);
}
