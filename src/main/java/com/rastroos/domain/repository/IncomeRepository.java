package com.rastroos.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.Income;

public interface IncomeRepository extends JpaRepository<Income, UUID> {

    Optional<Income> findByIdAndUserId(UUID id, UUID userId);

    List<Income> findAllByUserIdAndIncomeDateBetweenOrderByIncomeDateDesc(UUID userId,
                                                                         LocalDate start,
                                                                         LocalDate endExclusive);

    // ── Receita recorrente: lançamentos gerados por uma fonte ────────────

    List<Income> findAllByUserIdAndSourceIdAndIncomeDateGreaterThanEqualOrderByIncomeDateAsc(
            UUID userId, UUID sourceId, LocalDate from);

    long countByUserIdAndSourceId(UUID userId, UUID sourceId);

    long countByUserIdAndSourceIdAndIncomeDateGreaterThanEqual(UUID userId, UUID sourceId,
                                                               LocalDate from);

    void deleteByUserIdAndSourceId(UUID userId, UUID sourceId);

    void deleteByUserIdAndSourceIdAndIncomeDateGreaterThanEqual(UUID userId, UUID sourceId,
                                                                LocalDate from);

    /**
     * Os recebimentos das receitas fixas que caem no mês — um por fonte, é o
     * que a tela de receitas precisa para mostrar "cairá dia X" e o botão de
     * confirmar. Uma consulta só, em vez de uma por fonte.
     */
    @Query("""
            SELECT i FROM Income i
             WHERE i.userId = :userId
               AND i.sourceId IS NOT NULL
               AND i.incomeDate >= :start AND i.incomeDate < :endExclusive
             ORDER BY i.incomeDate ASC
            """)
    List<Income> findSourceOccurrencesInPeriod(@Param("userId") UUID userId,
                                               @Param("start") LocalDate start,
                                               @Param("endExclusive") LocalDate endExclusive);

    /** Tudo que está lançado no mês, confirmado ou não — a <b>previsão</b>. */
    @Query("""
            SELECT COALESCE(SUM(i.amountCents), 0) FROM Income i
            WHERE i.userId = :userId
              AND i.incomeDate >= :start AND i.incomeDate < :endExclusive
            """)
    long sumAmountByUserAndPeriod(@Param("userId") UUID userId,
                                  @Param("start") LocalDate start,
                                  @Param("endExclusive") LocalDate endExclusive);

    /** Só o que foi confirmado como recebido — o dinheiro que de fato entrou. */
    @Query("""
            SELECT COALESCE(SUM(i.amountCents), 0) FROM Income i
            WHERE i.userId = :userId
              AND i.received = true
              AND i.incomeDate >= :start AND i.incomeDate < :endExclusive
            """)
    long sumReceivedByUserAndPeriod(@Param("userId") UUID userId,
                                    @Param("start") LocalDate start,
                                    @Param("endExclusive") LocalDate endExclusive);

    /**
     * Busca paginada com filtros opcionais. {@code categoryId} nulo é
     * ignorado; {@code search} string vazia é ignorado (Postgres não infere
     * tipo {@code LOWER(NULL)}; passar {@code ""} mantém a query tipada).
     */
    @Query("""
            SELECT i FROM Income i
             WHERE i.userId = :userId
               AND i.incomeDate >= :start AND i.incomeDate < :endExclusive
               AND (:categoryId IS NULL OR i.category = :categoryId)
               AND (:search = '' OR LOWER(i.source) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Income> searchByFilters(@Param("userId") UUID userId,
                                 @Param("start") LocalDate start,
                                 @Param("endExclusive") LocalDate endExclusive,
                                 @Param("categoryId") String categoryId,
                                 @Param("search") String search,
                                 Pageable pageable);

    /**
     * Total acumulado aplicando os mesmos filtros de {@link #searchByFilters}.
     * Retorna sempre uma linha com {@code [total (long)]}.
     */
    @Query("""
            SELECT COALESCE(SUM(i.amountCents), 0)
              FROM Income i
             WHERE i.userId = :userId
               AND i.incomeDate >= :start AND i.incomeDate < :endExclusive
               AND (:categoryId IS NULL OR i.category = :categoryId)
               AND (:search = '' OR LOWER(i.source) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    long totalByFilters(@Param("userId") UUID userId,
                        @Param("start") LocalDate start,
                        @Param("endExclusive") LocalDate endExclusive,
                        @Param("categoryId") String categoryId,
                        @Param("search") String search);

    /** A parcela confirmada de {@link #totalByFilters}. */
    @Query("""
            SELECT COALESCE(SUM(i.amountCents), 0)
              FROM Income i
             WHERE i.userId = :userId
               AND i.received = true
               AND i.incomeDate >= :start AND i.incomeDate < :endExclusive
               AND (:categoryId IS NULL OR i.category = :categoryId)
               AND (:search = '' OR LOWER(i.source) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    long receivedTotalByFilters(@Param("userId") UUID userId,
                                @Param("start") LocalDate start,
                                @Param("endExclusive") LocalDate endExclusive,
                                @Param("categoryId") String categoryId,
                                @Param("search") String search);
}
