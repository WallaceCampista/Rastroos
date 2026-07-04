package com.rastroos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rastroos.domain.entity.Chat;

public interface ChatRepository extends JpaRepository<Chat, UUID> {

    Optional<Chat> findByIdAndUserId(UUID id, UUID userId);

    List<Chat> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
}
