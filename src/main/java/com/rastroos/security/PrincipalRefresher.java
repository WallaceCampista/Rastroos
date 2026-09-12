package com.rastroos.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Recarrega o principal autenticado a partir do banco e o regrava na sessão.
 *
 * <p>Nome, tema, paleta e o estado do onboarding são lidos do
 * {@link CustomUserDetails} guardado na sessão — não do banco a cada request.
 * Sem este refresh, o que o usuário acabou de salvar só apareceria no próximo
 * login. O username (email) não muda aqui.
 */
@Component
public class PrincipalRefresher {

    private final UserDetailsService userDetailsService;
    private final SecurityContextRepository securityContextRepository =
            new HttpSessionSecurityContextRepository();

    public PrincipalRefresher(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    public void refresh(HttpServletRequest request, HttpServletResponse response) {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current == null) {
            return;
        }
        UserDetails fresh = userDetailsService.loadUserByUsername(current.getName());
        UsernamePasswordAuthenticationToken refreshed =
                new UsernamePasswordAuthenticationToken(fresh, current.getCredentials(), fresh.getAuthorities());
        refreshed.setDetails(current.getDetails());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(refreshed);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
