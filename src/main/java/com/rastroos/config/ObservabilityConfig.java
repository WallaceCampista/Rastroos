package com.rastroos.config;

import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.rastroos.observability.MdcFilter;

/**
 * Fiação da observabilidade. Registra o {@link MdcFilter} logo <em>após</em> a
 * cadeia de filtros do Spring Security (ordem {@code DEFAULT_FILTER_ORDER + 10}),
 * garantindo que o {@code SecurityContext} já esteja disponível quando o
 * {@code userId} é lido para o MDC.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public FilterRegistrationBean<MdcFilter> mdcFilterRegistration() {
        FilterRegistrationBean<MdcFilter> registration = new FilterRegistrationBean<>(new MdcFilter());
        registration.setOrder(SecurityProperties.DEFAULT_FILTER_ORDER + 10);
        registration.addUrlPatterns("/*");
        return registration;
    }
}
