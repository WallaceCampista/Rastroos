package com.rastroos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.enums.AccountKind;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    List<Account> findAllByUserIdOrderByNameAsc(UUID userId);

    List<Account> findAllByUserIdAndKindOrderByNameAsc(UUID userId, AccountKind kind);

    long countByUserId(UUID userId);
}
