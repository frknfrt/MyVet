package com.vetos.platform.security;

import com.vetos.platform.web.RateLimitFilter;
import com.vetos.platform.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
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
    public SecurityFilterChain platformAdminSecurityFilterChain(HttpSecurity http, Environment env) throws Exception {
        http
            .securityMatcher("/api/v1/platform-admin/**")
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource(env)))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(handling -> handling
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
            )
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/v1/platform-admin/auth/**").permitAll()
                .anyRequest().authenticated()
            )
            // Kayit sirasi kritik -- Spring Security'nin FilterOrderRegistration'i, ozel
            // bir filtre sinifini ancak once addFilterBefore/After/At ile eklendikten
            // sonra "bilinen" sayar. Sira: once PlatformAdminAuthenticationFilter (bilinen
            // UsernamePasswordAuthenticationFilter.class'a ankorlanir), sonra RateLimitFilter
            // (artik bilinen PlatformAdminAuthenticationFilter.class'a), sonra RequestIdFilter
            // (artik bilinen RateLimitFilter.class'a) -- calisma zamani SIRASI ana
            // SecurityConfig ile birebir ayni: RequestIdFilter -> RateLimitFilter ->
            // PlatformAdminAuthenticationFilter -> UsernamePasswordAuthenticationFilter.
            .addFilterBefore(new PlatformAdminAuthenticationFilter(platformAdminJwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(new RateLimitFilter(), PlatformAdminAuthenticationFilter.class)
            .addFilterBefore(new RequestIdFilter(), RateLimitFilter.class);

        return http.build();
    }

    CorsConfigurationSource corsConfigurationSource(Environment env) {
        List<String> origins = new ArrayList<>(List.of(frontendBaseUrl));
        if (!List.of(env.getActiveProfiles()).contains("prod")) {
            origins.add("http://localhost:*");
        }
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
