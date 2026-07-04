package com.rastroos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.Investment;
import com.rastroos.domain.entity.enums.InvestmentKind;

public interface InvestmentRepository extends JpaRepository<Investment, UUID> {

    Optional<Investment> findByIdAndUserId(UUID id, UUID userId);

    List<Investment> findAllByUserIdOrderByNameAsc(UUID userId);

    List<Investment> findAllByUserIdAndKindOrderByNameAsc(UUID userId, InvestmentKind kind);

    @Query("""
            SELECT COALESCE(SUM(i.amountCents), 0) FROM Investment i
            WHERE i.userId = :userId
            """)
    long sumTotalByUser(@Param("userId") UUID userId);

    /**
     * Soma das metas dos cofrinhos do usuário (apenas PIGGY com goal != null).
     */
    @Query("""
            SELECT COALESCE(SUM(i.goalCents), 0) FROM Investment i
            WHERE i.userId = :userId
              AND i.kind = com.rastroos.domain.entity.enums.InvestmentKind.PIGGY
              AND i.goalCents IS NOT NULL
            """)
    long sumPiggyGoalsByUser(@Param("userId") UUID userId);

    /**
     * Soma do rendimento mensal estimado (monthly_return_cents) do usuário.
     */
    @Query("""
            SELECT COALESCE(SUM(i.monthlyReturnCents), 0) FROM Investment i
            WHERE i.userId = :userId
              AND i.monthlyReturnCents IS NOT NULL
            """)
    long sumMonthlyReturnByUser(@Param("userId") UUID userId);

    /**
     * Total investido por tipo. Linha: {@code [kind, total]}.
     */
    @Query("""
            SELECT i.kind, COALESCE(SUM(i.amountCents), 0)
              FROM Investment i
             WHERE i.userId = :userId
             GROUP BY i.kind
            """)
    List<Object[]> aggregateByKindAndUser(@Param("userId") UUID userId);

    long countByUserId(UUID userId);
}
