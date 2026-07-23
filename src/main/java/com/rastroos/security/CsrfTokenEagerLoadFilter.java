package com.rastroos.security;

import java.io.IOException;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Força a materialização do token CSRF (lazy no Spring Security 6) no começo da
 * requisição, antes de qualquer renderização de view.
 *
 * <p><strong>Por quê:</strong> páginas grandes (ex.: {@code landing.html}) podem
 * ter a resposta <em>committed</em> — buffer do Tomcat cheio e primeiros bytes já
 * enviados — antes de o formulário com {@code th:action} ser processado. Nesse
 * instante o {@code HttpSessionCsrfTokenRepository} tenta criar a
 * {@code HttpSession} para gravar o token e falha com
 * {@code IllegalStateException: Cannot create a session after the response has
 * been committed}, derrubando o render. Resolvendo o token aqui, a sessão é
 * criada enquanto a resposta ainda está aberta.
 *
 * <p>Registrado <em>depois</em> do {@code CsrfFilter} para que o atributo
 * {@link CsrfToken} já esteja disponível na requisição.
 */
public class CsrfTokenEagerLoadFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (token != null) {
            // Acessa o valor para disparar o carregamento/gravação em sessão
            // agora, enquanto a resposta ainda não foi committed.
            token.getToken();
        }
        filterChain.doFilter(request, response);
    }
}
