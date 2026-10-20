package com.vetos.platform.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import com.vetos.platform.web.RateLimitFilter;
import com.vetos.platform.web.RequestIdFilter;
import com.vetos.platform.web.TenantRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Ucuncu, bagimsiz SecurityFilterChain -- sadece /api/v1/tarbil-extension/**.
 * PlatformAdminSecurityConfig ile ayni desen; SecurityConfig degistirilmedi.
 * CORS kapali: eklenti istekleri host_permissions'li service worker'dan gelir.
 */
@Configuration
@RequiredArgsConstructor
public class TarbilExtensionSecurityConfig {

    private final ExtensionTokenAuthenticator extensionTokenAuthenticator;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;
    private final ObjectMapper objectMapper;

    @Bean
    @Order(2)
    public SecurityFilterChain tarbilExtensionSecurityFilterChain(
        HttpSecurity http,
        TenantRateLimiter tenantRateLimiter,
        @Value("${vetly.extension.min-version:0.1.0}") String minVersion
    ) throws Exception {
        http
            .securityMatcher("/api/v1/tarbil-extension/**")
            // CSRF kapali: SecurityConfig ile ayni gerekce (STATELESS + Authorization header).
            .csrf(AbstractHttpConfigurer::disable)
            .cors(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/tarbil-extension/pair").permitAll()
                .anyRequest().hasRole(ExtensionTokenAuthenticationFilter.ROLE)
            )
            // Kayit sirasi PlatformAdminSecurityConfig ile ayni gerekceyle.
            .addFilterBefore(new ExtensionTokenAuthenticationFilter(extensionTokenAuthenticator), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new RateLimitFilter(), ExtensionTokenAuthenticationFilter.class)
            // Faz 7: eski eklenti token dogrulamasina hic ulasmaz; RateLimit'ten sonra kaydedilmeli (ayni sira, kayit sirasi belirler).
            .addFilterBefore(new ClientVersionFilter(ClientVersion.parse(minVersion), objectMapper), ExtensionTokenAuthenticationFilter.class)
            .addFilterBefore(new RequestIdFilter(), RateLimitFilter.class)
            .addFilterAfter(new TenantRateLimitFilter(tenantRateLimiter), ExtensionTokenAuthenticationFilter.class);
        return http.build();
    }
}
