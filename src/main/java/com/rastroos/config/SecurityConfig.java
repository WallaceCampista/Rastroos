package com.rastroos.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

import com.rastroos.security.BruteForceFilter;
import com.rastroos.security.CsrfTokenEagerLoadFilter;
import com.rastroos.security.CustomUserDetailsService;
import com.rastroos.security.LockoutPreAuthFilter;
import com.rastroos.security.LoginFailureHandler;
import com.rastroos.security.LoginSuccessHandler;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final LoginSuccessHandler successHandler;
    private final LoginFailureHandler failureHandler;
    private final BruteForceFilter bruteForceFilter;
    private final LockoutPreAuthFilter lockoutFilter;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                          LoginSuccessHandler successHandler,
                          LoginFailureHandler failureHandler,
                          BruteForceFilter bruteForceFilter,
                          LockoutPreAuthFilter lockoutFilter) {
        this.userDetailsService = userDetailsService;
        this.successHandler = successHandler;
        this.failureHandler = failureHandler;
        this.bruteForceFilter = bruteForceFilter;
        this.lockoutFilter = lockoutFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(DaoAuthenticationProvider provider) {
        return new org.springframework.security.authentication.ProviderManager(provider);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/landing", "/auth/**",
                                 "/css/**", "/js/**", "/images/**", "/fonts/**",
                                 "/webjars/**",
                                 // health + probes (liveness/readiness) públicos p/ orquestrador;
                                 // demais actuator (prometheus, env, ...) exigem auth (§3.2)
                                 "/actuator/health/**", "/actuator/info",
                                 "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                    .permitAll()
                .requestMatchers("/app/users/**", "/api/admin/**")
                    .hasRole("ADMIN")
                .requestMatchers("/app/**", "/api/**")
                    .authenticated()
                .anyRequest().authenticated()
            )
            .sessionManagement(sm -> sm
                .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation(sf -> sf.changeSessionId())
                .maximumSessions(5)
                .maxSessionsPreventsLogin(false)
            )
            .formLogin(form -> form
                // O login mora na landing (drawer). loginPage = URL de redirect
                // quando exige auth: abre o drawer de login na LP. O POST de
                // processamento continua em /auth/login (BruteForce/Lockout casam nele).
                .loginPage("/?openLogin=login")
                .loginProcessingUrl("/auth/login")
                .usernameParameter("username")
                .passwordParameter("password")
                .successHandler(successHandler)
                .failureHandler(failureHandler)
                .permitAll()
            )
            .logout(logout -> logout
                .logoutUrl("/auth/logout")
                .logoutSuccessUrl("/?logout")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID")
                .permitAll()
            )
            .headers(h -> h
                .contentTypeOptions(opts -> {})
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31_536_000)
                )
                .referrerPolicy(rp -> rp
                        .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .xssProtection(xss -> xss
                        .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK))
                .contentSecurityPolicy(csp -> csp
                        .policyDirectives(
                            "default-src 'self'; "
                          + "script-src 'self'; "
                          + "style-src 'self' https://fonts.googleapis.com; "
                          + "font-src 'self' https://fonts.gstatic.com; "
                          + "img-src 'self' data:; "
                          + "connect-src 'self'; "
                          + "object-src 'none'; "
                          + "frame-ancestors 'none'; "
                          + "base-uri 'self'; "
                          + "form-action 'self'"))
                .permissionsPolicyHeader(pp -> pp
                        .policy("camera=(), microphone=(), geolocation=()"))
            )
            .authenticationProvider(authenticationProvider())
            // Materializa o token CSRF antes do render (evita
            // "Cannot create a session after the response has been committed"
            // em páginas grandes com <form th:action>). Ver CsrfTokenEagerLoadFilter.
            .addFilterAfter(new CsrfTokenEagerLoadFilter(), CsrfFilter.class)
            .addFilterBefore(bruteForceFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(lockoutFilter,    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
