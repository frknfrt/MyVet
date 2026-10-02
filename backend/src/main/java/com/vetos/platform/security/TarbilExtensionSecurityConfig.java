package com.vetos.platform.security;

import com.vetos.platform.web.RateLimitFilter;
import com.vetos.platform.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
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

    @Bean
    @Order(2)
    public SecurityFilterChain tarbilExtensionSecurityFilterChain(HttpSecurity http) throws Exception {
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
            .addFilterBefore(new RequestIdFilter(), RateLimitFilter.class);
        return http.build();
    }
}
