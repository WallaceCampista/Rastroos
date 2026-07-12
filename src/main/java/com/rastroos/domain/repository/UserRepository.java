package com.rastroos.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rastroos.domain.entity.User;
import com.rastroos.domain.entity.enums.UserRole;
import com.rastroos.domain.entity.enums.UserStatus;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    long countByStatus(UserStatus status);

    long countByRole(UserRole role);

    long countByRoleAndStatus(UserRole role, UserStatus status);

    /** Alvos elegíveis para um acessor: usuários comuns ativos. */
    List<User> findByRoleAndStatusOrderByNameAsc(UserRole role, UserStatus status);

    /** Contas acessor vinculadas a um titular. */
    List<User> findByAccessesUserIdOrderByCreatedAtAsc(UUID accessesUserId);

    long countByAccessesUserId(UUID accessesUserId);

    /**
     * Busca de admin: filtro opcional por status ({@code null} = todos) e por
     * nome/email. Para "sem busca" passe {@code ""} (string vazia) — Postgres
     * não infere o tipo de {@code LOWER(NULL)} dentro do {@code CONCAT}, então
     * usamos o sentinela vazio como nas demais buscas (ver adminSearch).
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:q = '' OR LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%'))
                           OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%')))
              AND (:status IS NULL OR u.status = :status)
            ORDER BY u.createdAt DESC
            """)
    Page<User> search(@Param("q") String q,
                      @Param("status") UserStatus status,
                      Pageable pageable);
}
