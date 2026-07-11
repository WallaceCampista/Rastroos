package com.rastroos.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.SupportTicket;
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

    long countByStatus(SupportTicketStatus status);
}
