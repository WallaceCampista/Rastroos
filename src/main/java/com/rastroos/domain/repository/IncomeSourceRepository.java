package com.rastroos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rastroos.domain.entity.IncomeSource;

public interface IncomeSourceRepository extends JpaRepository<IncomeSource, UUID> {

    Optional<IncomeSource> findByIdAndUserId(UUID id, UUID userId);

    List<IncomeSource> findAllByUserIdOrderByNameAsc(UUID userId);

    /** Só as fontes ainda ativas — as que o formulário de lançamento oferece. */
    List<IncomeSource> findAllByUserIdAndClosedAtIsNullOrderByNameAsc(UUID userId);

    boolean existsByUserIdAndNameIgnoreCase(UUID userId, String name);

    boolean existsByUserIdAndNameIgnoreCaseAndIdNot(UUID userId, String name, UUID id);
}
