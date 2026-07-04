package com.rastroos.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * {@link Clock} injetado em services/controllers que dependem de "agora".
 * Permite congelar o relógio em testes via {@code Clock.fixed(...)}.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
