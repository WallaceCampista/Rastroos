package com.rastroos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.Account;
import com.rastroos.domain.entity.enums.AccountKind;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndUserId(UUID id, UUID userId);

    /**
     * Mesma busca, travando a linha até o fim da transação. Serializa duas
     * importações de fatura da mesma conta (duplo clique, duas abas): a segunda
     * só lê os lançamentos depois que a primeira gravou, e não duplica nada.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id AND a.userId = :userId")
    Optional<Account> lockByIdAndUserId(@Param("id") UUID id, @Param("userId") UUID userId);

    List<Account> findAllByUserIdOrderByNameAsc(UUID userId);

    List<Account> findAllByUserIdAndKindOrderByNameAsc(UUID userId, AccountKind kind);

    long countByUserId(UUID userId);
}
