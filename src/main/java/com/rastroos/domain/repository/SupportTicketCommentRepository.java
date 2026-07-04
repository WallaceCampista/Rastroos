package com.rastroos.domain.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rastroos.domain.entity.SupportTicketComment;

public interface SupportTicketCommentRepository
        extends JpaRepository<SupportTicketComment, UUID> {

    List<SupportTicketComment> findAllByTicketIdOrderByCreatedAtAsc(String ticketId);

    long countByTicketId(String ticketId);
}
