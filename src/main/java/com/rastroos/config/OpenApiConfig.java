package com.rastroos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

/**
 * Metadados da API REST expostos pelo springdoc-openapi em /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rastroosOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Rastro$ API")
                .description("API REST do sistema de controle financeiro Rastro$")
                .version("v0.1.0")
                .contact(new Contact().name("Rastro$ team"))
                .license(new License().name("Private")));
    }
}
