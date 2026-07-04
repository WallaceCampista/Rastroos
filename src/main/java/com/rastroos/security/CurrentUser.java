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
}
