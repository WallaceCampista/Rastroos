package com.rastroos.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.AiUserState;

/**
 * Estado de IA por usuário. O {@code bump} é um UPDATE atômico (não um
 * read-modify-write) para que duas escritas simultâneas do mesmo usuário não
 * percam versão — se perdessem, uma alteração poderia nunca ser refletida no
 * resumo.
 */
public interface AiUserStateRepository extends JpaRepository<AiUserState, UUID> {

    Optional<AiUserState> findByUserId(UUID userId);

    /**
     * Marca o usuário como "mudou algo": incrementa a versão e, se ainda não
     * havia rajada em curso, abre a janela de debounce.
     *
     * @return quantas linhas foram afetadas (0 = usuário ainda sem estado)
     */
    @Modifying
    @Query("""
            UPDATE AiUserState s
               SET s.dataVersion = s.dataVersion + 1,
                   s.dirtySince = COALESCE(s.dirtySince, :now)
             WHERE s.userId = :userId
            """)
    int bump(@Param("userId") UUID userId, @Param("now") Instant now);

    /** Usuários com rajada de escrita já encerrada (debounce vencido). */
    @Query("""
            SELECT s FROM AiUserState s
             WHERE s.dirtySince IS NOT NULL
               AND s.dirtySince <= :threshold
             ORDER BY s.dirtySince ASC
            """)
    List<AiUserState> findDueForWarmup(@Param("threshold") Instant threshold, Limit limit);
}
