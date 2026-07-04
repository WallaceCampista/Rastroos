package com.rastroos.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserStatus;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByStatus(UserStatus status);

    @Query("""
            SELECT u FROM User u
            WHERE (:q IS NULL OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%'))
                              OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR u.status = :status)
            """)
    Page<User> search(@Param("q") String q,
                      @Param("status") UserStatus status,
                      Pageable pageable);
}
