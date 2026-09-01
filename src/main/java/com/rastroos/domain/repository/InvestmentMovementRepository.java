package com.rastroos.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rastroos.domain.entity.InvestmentMovement;

public interface InvestmentMovementRepository extends JpaRepository<InvestmentMovement, Long> {

    /** Movimentos do investimento, mais recentes primeiro. */
    List<InvestmentMovement> findAllByInvestmentIdOrderByOccurredAtDescIdDesc(UUID investmentId);

    /** Remove todos os movimentos de um investimento (limpeza ao excluir). */
    long deleteByInvestmentId(UUID investmentId);
}
