package com.rastroos.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.SupportTicket;
import com.rastroos.domain.entity.enums.SupportTicketCategory;
import com.rastroos.domain.entity.enums.SupportTicketPriority;
import com.rastroos.domain.entity.enums.SupportTicketStatus;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, String> {

    /** Visão do usuário comum. */
    Optional<SupportTicket> findByIdAndUserId(String id, UUID userId);

    Page<SupportTicket> findAllByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<SupportTicket> findAllByUserIdAndStatusOrderByCreatedAtDesc(UUID userId,
                                                                    SupportTicketStatus status,
                                                                    Pageable pageable);

    long countByUserId(UUID userId);

    long countByUserIdAndStatus(UUID userId, SupportTicketStatus status);

    /**
     * Visão do admin: todos os tickets, com filtros opcionais. {@code status}
     * nulo = todos. Para o título passe {@code ""} (string vazia) para "sem
     * filtro" — Postgres não infere o tipo de {@code LOWER(NULL)} dentro do
     * {@code CONCAT}, então usamos o sentinela vazio como em outras buscas.
     */
    @Query("""
            SELECT t FROM SupportTicket t
            WHERE (:status IS NULL OR t.status = :status)
              AND (:q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY t.createdAt DESC
            """)
    Page<SupportTicket> adminSearch(@Param("q") String q,
                                    @Param("status") SupportTicketStatus status,
                                    Pageable pageable);

    /**
     * Busca unificada. {@code userId} nulo = todos (visão admin); preenchido =
     * só os chamados do usuário. Filtros de status/prioridade/categoria nulos =
     * sem filtro. {@code q} vazio ("") = sem busca por título/id.
     */
    @Query("""
            SELECT t FROM SupportTicket t
            WHERE (:userId IS NULL OR t.userId = :userId)
              AND (:status IS NULL OR t.status = :status)
              AND (:priority IS NULL OR t.priority = :priority)
              AND (:category IS NULL OR t.category = :category)
              AND (:q = '' OR LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
                          OR LOWER(t.id) LIKE LOWER(CONCAT('%', :q, '%')))
            ORDER BY t.createdAt DESC
            """)
    Page<SupportTicket> search(@Param("userId") UUID userId,
                               @Param("status") SupportTicketStatus status,
                               @Param("priority") SupportTicketPriority priority,
                               @Param("category") SupportTicketCategory category,
                               @Param("q") String q,
                               Pageable pageable);

    long countByStatus(SupportTicketStatus status);
}
