package com.vetos.platform.security;

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
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * SecurityConfig'ten tamamen bagimsiz, ikinci bir SecurityFilterChain.
 * Sadece /api/v1/platform-admin/** ile eslesir (@Order(1) ile once
 * degerlendirilir), TenantContext'e hic dokunmaz. SecurityConfig.java
 * bu dosya eklenirken degistirilmedi -- mevcut klinik auth akisina
 * sifir risk.
 */
@Configuration
@RequiredArgsConstructor
public class PlatformAdminSecurityConfig {

    private final PlatformAdminJwtTokenProvider platformAdminJwtTokenProvider;
    private final JsonAuthenticationEntryPoint authenticationEntryPoint;
    private final JsonAccessDeniedHandler accessDeniedHandler;

    // Platform admin paneli, ana uygulamayla ayni React uygulamasi icinde
    // (/platform-admin/* rotasi) sunuluyor -- bu yuzden ayni frontend origin'i
    // gerekiyor. SecurityConfig'ten BAGIMSIZ bir CORS yapilandirmasi oldugu
    // icin ayni degerin burada da tekrar tanimlanmasi gerekiyor.
    @Value("${app.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Bean
    @Order(1)
    public SecurityFilterChain platformAdminSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/v1/platform-admin/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/platform-admin/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(new PlatformAdminAuthenticationFilter(platformAdminJwtTokenProvider), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("http://localhost:*", frontendBaseUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
