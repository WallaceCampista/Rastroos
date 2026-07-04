package com.rastroos.domain.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.VerificationCode;
import com.rastroos.domain.entity.enums.VerificationPurpose;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    Optional<VerificationCode> findFirstByUserIdAndPurposeAndUsedAtIsNullOrderByExpiresAtDesc(
            UUID userId, VerificationPurpose purpose);

    @Modifying
    @Query("DELETE FROM VerificationCode vc WHERE vc.userId = :userId AND vc.purpose = :purpose")
    int deleteAllForUserAndPurpose(@Param("userId") UUID userId,
                                   @Param("purpose") VerificationPurpose purpose);

    @Modifying
    @Query("DELETE FROM VerificationCode vc WHERE vc.expiresAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
