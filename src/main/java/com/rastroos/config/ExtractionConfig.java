package com.rastroos.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Habilita a vinculação de {@link ExtractionProperties}. A extração em si vive
 * em {@code ExpenseExtractionService}, que decide entre stub e IA real a partir
 * dessas propriedades.
 */
@Configuration
@EnableConfigurationProperties(ExtractionProperties.class)
public class ExtractionConfig {
}
