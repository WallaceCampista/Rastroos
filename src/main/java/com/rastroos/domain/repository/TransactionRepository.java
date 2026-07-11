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

import com.rastroos.domain.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    Page<Transaction> findAllByUserIdAndDueDateBetween(UUID userId,
                                                       LocalDate start,
                                                       LocalDate endExclusive,
                                                       Pageable pageable);

    List<Transaction> findAllByUserIdAndDueDateBetweenOrderByDueDateAsc(UUID userId,
                                                                       LocalDate start,
                                                                       LocalDate endExclusive);

    List<Transaction> findAllByUserIdAndAccountIdAndDueDateBetween(UUID userId,
                                                                  UUID accountId,
                                                                  LocalDate start,
                                                                  LocalDate endExclusive);

    @Query("""
            SELECT t FROM Transaction t
            WHERE t.userId = :userId
              AND t.paid = false
              AND t.dueDate >= :from
            ORDER BY t.dueDate ASC
            """)
    List<Transaction> findUpcomingUnpaid(@Param("userId") UUID userId,
                                         @Param("from") LocalDate from,
                                         Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(t.amountCents), 0) FROM Transaction t
            WHERE t.userId = :userId
              AND t.dueDate >= :start AND t.dueDate < :endExclusive
            """)
    long sumAmountByUserAndPeriod(@Param("userId") UUID userId,
                                  @Param("start") LocalDate start,
                                  @Param("endExclusive") LocalDate endExclusive);

    @Query("""
            SELECT COALESCE(SUM(t.amountCents), 0) FROM Transaction t
            WHERE t.userId = :userId
              AND t.paid = true
              AND t.dueDate >= :start AND t.dueDate < :endExclusive
            """)
    long sumPaidByUserAndPeriod(@Param("userId") UUID userId,
                                @Param("start") LocalDate start,
                                @Param("endExclusive") LocalDate endExclusive);

    /**
     * Soma total e total pago, por conta, no período. Linha por conta:
     * {@code [accountId (UUID), total (long), paid (long), count (long)]}.
     */
    @Query("""
            SELECT t.accountId,
                   COALESCE(SUM(t.amountCents), 0),
                   COALESCE(SUM(CASE WHEN t.paid = true THEN t.amountCents ELSE 0 END), 0),
                   COUNT(t)
              FROM Transaction t
             WHERE t.userId = :userId
               AND t.dueDate >= :start AND t.dueDate < :endExclusive
             GROUP BY t.accountId
            """)
    List<Object[]> aggregateByAccountAndPeriod(@Param("userId") UUID userId,
                                               @Param("start") LocalDate start,
                                               @Param("endExclusive") LocalDate endExclusive);

    /**
     * Soma de gastos por categoria no período. Linha:
     * {@code [categoryId (String), total (long)]}, ordenada desc por total.
     */
    @Query("""
            SELECT t.categoryId, COALESCE(SUM(t.amountCents), 0) AS total
              FROM Transaction t
             WHERE t.userId = :userId
               AND t.dueDate >= :start AND t.dueDate < :endExclusive
             GROUP BY t.categoryId
             ORDER BY total DESC
            """)
    List<Object[]> aggregateByCategoryAndPeriod(@Param("userId") UUID userId,
                                                @Param("start") LocalDate start,
                                                @Param("endExclusive") LocalDate endExclusive);

    /**
     * Totais do período em uma linha só: {@code [total (long), paid (long),
     * fixed (long)]}. Usado pelos agregadores de relatório/comparativo para
     * evitar três queries separadas por mês.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amountCents), 0),
                   COALESCE(SUM(CASE WHEN t.paid = true THEN t.amountCents ELSE 0 END), 0),
                   COALESCE(SUM(CASE WHEN t.fixed = true THEN t.amountCents ELSE 0 END), 0)
              FROM Transaction t
             WHERE t.userId = :userId
               AND t.dueDate >= :start AND t.dueDate < :endExclusive
            """)
    List<Object[]> aggregateTotalsByPeriod(@Param("userId") UUID userId,
                                           @Param("start") LocalDate start,
                                           @Param("endExclusive") LocalDate endExclusive);

    long countByUserIdAndPaidFalse(UUID userId);

    long countByUserIdAndAccountId(UUID userId, UUID accountId);

    /**
     * Busca paginada com filtros opcionais. Cada filtro nulo é ignorado:
     * passar {@code null} significa "todos". Filtro {@code search} bate no
     * {@code description} ({@code LIKE %?%} case-insensitive).
     */
    @Query("""
            SELECT t FROM Transaction t
             WHERE t.userId = :userId
               AND t.dueDate >= :start AND t.dueDate < :endExclusive
               AND (:paid IS NULL OR t.paid = :paid)
               AND (:fixed IS NULL OR t.fixed = :fixed)
               AND (:accountId IS NULL OR t.accountId = :accountId)
               AND (:categoryId IS NULL OR t.categoryId = :categoryId)
               AND (:search = '' OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    Page<Transaction> searchByFilters(@Param("userId") UUID userId,
                                      @Param("start") LocalDate start,
                                      @Param("endExclusive") LocalDate endExclusive,
                                      @Param("paid") Boolean paid,
                                      @Param("fixed") Boolean fixed,
                                      @Param("accountId") UUID accountId,
                                      @Param("categoryId") String categoryId,
                                      @Param("search") String search,
                                      Pageable pageable);

    /**
     * Totais (amount, paid) aplicando os mesmos filtros de
     * {@link #searchByFilters}. Linha: {@code [total (long), paid (long)]}.
     */
    @Query("""
            SELECT COALESCE(SUM(t.amountCents), 0),
                   COALESCE(SUM(CASE WHEN t.paid = true THEN t.amountCents ELSE 0 END), 0)
              FROM Transaction t
             WHERE t.userId = :userId
               AND t.dueDate >= :start AND t.dueDate < :endExclusive
               AND (:paid IS NULL OR t.paid = :paid)
               AND (:fixed IS NULL OR t.fixed = :fixed)
               AND (:accountId IS NULL OR t.accountId = :accountId)
               AND (:categoryId IS NULL OR t.categoryId = :categoryId)
               AND (:search = '' OR LOWER(t.description) LIKE LOWER(CONCAT('%', :search, '%')))
            """)
    List<Object[]> totalsByFilters(@Param("userId") UUID userId,
                                   @Param("start") LocalDate start,
                                   @Param("endExclusive") LocalDate endExclusive,
                                   @Param("paid") Boolean paid,
                                   @Param("fixed") Boolean fixed,
                                   @Param("accountId") UUID accountId,
                                   @Param("categoryId") String categoryId,
                                   @Param("search") String search);
}
