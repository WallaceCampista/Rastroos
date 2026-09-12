package com.rastroos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rastroos.domain.entity.AiInsight;

/**
 * Resumos de tela já gerados. Sem regra de negócio: a decisão de reaproveitar
 * ou regerar mora em {@code ScreenInsightService}.
 */
public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {

    Optional<AiInsight> findByUserIdAndScreenAndPeriod(UUID userId, String screen, String period);

    List<AiInsight> findAllByUserId(UUID userId);

    long deleteByUserId(UUID userId);
}
