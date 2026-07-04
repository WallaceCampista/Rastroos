package com.rastroos.domain.repository;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.LoginAttempt;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, Long> {

    @Query("""
            SELECT COUNT(la) FROM LoginAttempt la
            WHERE LOWER(la.email) = LOWER(:email)
              AND la.success = false
              AND la.attemptedAt >= :since
            """)
    long countFailuresSince(@Param("email") String email,
                            @Param("since") Instant since);

    @Query("""
            SELECT COUNT(la) FROM LoginAttempt la
            WHERE la.ipAddress = :ip
              AND la.success = false
              AND la.attemptedAt >= :since
            """)
    long countFailuresByIpSince(@Param("ip") String ip,
                                @Param("since") Instant since);
}
