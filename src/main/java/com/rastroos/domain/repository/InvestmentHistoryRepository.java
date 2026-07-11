package com.rastroos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.InvestmentHistory;

public interface InvestmentHistoryRepository extends JpaRepository<InvestmentHistory, Long> {

    List<InvestmentHistory> findAllByInvestmentIdOrderByYearMonthAsc(UUID investmentId);

    Optional<InvestmentHistory> findByInvestmentIdAndYearMonth(UUID investmentId, String yearMonth);

    /**
     * Todos os snapshots dos investimentos de um usuário, ordenados por
     * investimento e mês. O filtro por {@code userId} é aplicado via subquery
     * em {@code investments} — {@code investment_history} não tem coluna própria
     * de usuário. Usado pelo comparativo para estimar o aporte mensal.
     */
    @Query("""
            SELECT h FROM InvestmentHistory h
             WHERE h.investmentId IN (
                   SELECT i.id FROM Investment i WHERE i.userId = :userId)
             ORDER BY h.investmentId ASC, h.yearMonth ASC
            """)
    List<InvestmentHistory> findAllByUserId(@Param("userId") UUID userId);
}
