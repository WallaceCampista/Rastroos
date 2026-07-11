package com.rastroos.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita a vinculação de {@link AlfredoProperties}. O cliente HTTP em si é
 * construído dentro de {@code AlfredoAiClient} a partir dessas propriedades,
 * para não criar um {@code RestClient} quando a integração está desligada.
 */
@Configuration
@EnableConfigurationProperties(AlfredoProperties.class)
public class AlfredoConfig {
}
