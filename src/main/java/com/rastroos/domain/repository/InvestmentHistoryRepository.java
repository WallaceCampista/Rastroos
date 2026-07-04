package com.rastroos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rastroos.domain.entity.InvestmentHistory;

public interface InvestmentHistoryRepository extends JpaRepository<InvestmentHistory, Long> {

    List<InvestmentHistory> findAllByInvestmentIdOrderByYearMonthAsc(UUID investmentId);

    Optional<InvestmentHistory> findByInvestmentIdAndYearMonth(UUID investmentId, String yearMonth);
}
