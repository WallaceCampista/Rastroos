package com.rastroos.domain.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.UserSession;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {

    List<UserSession> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(UUID userId);

    Optional<UserSession> findByIdAndUserId(UUID id, UUID userId);

    Optional<UserSession> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :revokedAt "
         + "WHERE s.userId = :userId AND s.revokedAt IS NULL")
    int revokeAllByUser(@Param("userId") UUID userId,
                        @Param("revokedAt") Instant revokedAt);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :revokedAt "
         + "WHERE s.id = :id AND s.userId = :userId AND s.revokedAt IS NULL")
    int revokeByIdAndUser(@Param("id") UUID id,
                          @Param("userId") UUID userId,
                          @Param("revokedAt") Instant revokedAt);
}
