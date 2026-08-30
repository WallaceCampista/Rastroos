package com.rastroos.security;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Anti brute force camada 2 — bloqueia POST /auth/login se o email
 * está em lockout (5 falhas em 15 min por default). Redireciona para
 * a landing (drawer de login) com error=locked. Roda antes do filtro de autenticação.
 */
@Component
public class LockoutPreAuthFilter extends OncePerRequestFilter {

    private final LockoutChecker lockout;

    public LockoutPreAuthFilter(LockoutChecker lockout) {
        this.lockout = lockout;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod())
                 && "/auth/login".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String email = request.getParameter("username");
        if (email != null && !email.isBlank() && lockout.isLocked(email)) {
            String encoded = URLEncoder.encode(email, StandardCharsets.UTF_8);
            response.sendRedirect("/?openLogin=login&error=locked&email=" + encoded);
            return;
        }
        chain.doFilter(request, response);
    }
}
