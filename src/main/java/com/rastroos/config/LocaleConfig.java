package com.rastroos.config;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;

import com.rastroos.web.interceptor.TopbarChipsInterceptor;

/**
 * i18n: resolve o locale a partir do header Accept-Language (default pt-BR)
 * e permite override via ?lang=pt|en para troca rápida na UI.
 */
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    // ObjectProvider (e não injeção direta) para não exigir o interceptor no
    // contexto: em fatias @WebMvcTest o TopbarChipsInterceptor não é carregado,
    // e essa configuração ainda precisa subir sem quebrar. Em produção o bean
    // existe e é registrado normalmente.
    private final ObjectProvider<TopbarChipsInterceptor> topbarChipsInterceptor;

    public LocaleConfig(ObjectProvider<TopbarChipsInterceptor> topbarChipsInterceptor) {
        this.topbarChipsInterceptor = topbarChipsInterceptor;
    }

    public static final Locale DEFAULT_LOCALE = Locale.of("pt", "BR");
    public static final List<Locale> SUPPORTED_LOCALES = List.of(
            DEFAULT_LOCALE,
            Locale.ENGLISH
    );

    /**
     * PT-BR é o padrão (independente do Accept-Language do navegador, já que a UI
     * é servida em português). O idioma fica na sessão e pode ser trocado via
     * {@code ?lang=en} pelo {@link #localeChangeInterceptor()}.
     */
    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver resolver = new SessionLocaleResolver();
        resolver.setDefaultLocale(DEFAULT_LOCALE);
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
        topbarChipsInterceptor.ifAvailable(registry::addInterceptor);
    }
}
