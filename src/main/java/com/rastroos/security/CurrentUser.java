package com.rastroos.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Acesso curto ao usuário autenticado a partir do contexto. Serviços que
 * filtram por {@code userId} devem chamar {@link #requireId()} para falhar
 * cedo se o contexto estiver vazio.
 */
@Component
public class CurrentUser {

    public Optional<CustomUserDetails> get() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return Optional.empty();
        Object principal = auth.getPrincipal();
        return principal instanceof CustomUserDetails details
                ? Optional.of(details)
                : Optional.empty();
    }

    public Optional<UUID> id() {
        return get().map(CustomUserDetails::getId);
    }

    public UUID requireId() {
        return id().orElseThrow(() ->
                new IllegalStateException("No authenticated user in SecurityContext"));
    }

    /** {@code true} se o usuário autenticado tem a authority {@code ROLE_ADMIN}. */
    public boolean isAdmin() {
        return get()
                .map(u -> u.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())))
                .orElse(false);
    }

    /** {@code true} se a conta autenticada é um ACESSOR (opera dados de outro usuário). */
    public boolean isAccessor() {
        return get().map(CustomUserDetails::isAccessor).orElse(false);
    }

    /**
     * Id do <em>dono dos dados</em>: para um ACESSOR é o usuário-alvo
     * ({@code accessesUserId}); para os demais, o próprio id. É este o id que
     * os controllers/serviços de dados financeiros devem usar para filtrar —
     * derivado sempre do principal autenticado, nunca de parâmetro do request.
     */
    public Optional<UUID> effectiveUserId() {
        return get().map(u -> u.isAccessor() ? u.getAccessesUserId() : u.getId());
    }

    public UUID requireEffectiveId() {
        return effectiveUserId().orElseThrow(() ->
                new IllegalStateException("No effective data owner in SecurityContext"));
    }

    /** Contas ACESSOR não podem excluir nada. */
    public boolean canDelete() {
        return !isAccessor();
    }

    /** {@code true} quando o acessor logado está com valores mascarados pelo titular. */
    public boolean isMaskActive() {
        return get().map(u -> u.isAccessor() && u.isValuesMasked()).orElse(false);
    }

    /** Nome do usuário-alvo (para o banner), quando a conta é um ACESSOR. */
    public Optional<String> accessTargetName() {
        return get().map(CustomUserDetails::getTargetName);
    }
}
