package com.rastroos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * Metadados da API REST expostos pelo springdoc-openapi em /swagger-ui.html.
 *
 * <p>Autenticação: a API é <b>stateful</b> (sessão + cookie). O documento
 * declara um esquema {@code apiKey} sobre o cookie de sessão
 * ({@code JSESSIONID}); combinado com {@code springdoc.swagger-ui.with-credentials=true},
 * o Swagger UI reaproveita a sessão do navegador (mesmo host) — basta o usuário
 * ter feito login no app (landing em {@code /}). Operações de escrita ainda exigem o
 * token CSRF (enviado pelos formulários Thymeleaf); o Swagger UI serve bem para
 * explorar e testar leituras.
 */
@Configuration
public class OpenApiConfig {

    /** Nome do esquema de segurança referenciado nas operações. */
    public static final String SESSION_COOKIE_SCHEME = "sessionCookie";

    @Bean
    public OpenAPI rastroosOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Rastro$ API")
                        .description("""
                                API REST do sistema de controle financeiro pessoal Rastro$.

                                Todos os recursos são isolados por usuário: cada operação enxerga \
                                apenas os dados do usuário autenticado (acesso cruzado retorna 404). \
                                Rotas sob /api/admin exigem o papel ADMIN.""")
                        .version("v0.1.0")
                        .contact(new Contact().name("Rastro$ team"))
                        .license(new License().name("Private")))
                .components(new Components()
                        .addSecuritySchemes(SESSION_COOKIE_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("JSESSIONID")
                                .description("""
                                        Sessão autenticada do Rastro$. Faça login no app (landing em /) no \
                                        mesmo navegador; o cookie JSESSIONID é enviado automaticamente \
                                        (same-origin, with-credentials). Operações de escrita \
                                        (POST/PUT/PATCH/DELETE) também exigem o token CSRF.""")))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE_SCHEME));
    }
}
