package com.vetos.platform.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SecurityConfigCorsTest {

    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private JsonAuthenticationEntryPoint authenticationEntryPoint;
    @Mock private JsonAccessDeniedHandler accessDeniedHandler;
    @Mock private Environment environment;

    private SecurityConfig aSecurityConfig() {
        SecurityConfig securityConfig = new SecurityConfig(jwtTokenProvider, authenticationEntryPoint, accessDeniedHandler);
        ReflectionTestUtils.setField(securityConfig, "vetlySiteOrigin", "https://vetly.example.com");
        ReflectionTestUtils.setField(securityConfig, "frontendBaseUrl", "https://app.vetly.example.com");
        return securityConfig;
    }

    private CorsConfiguration resolveConfig(CorsConfigurationSource source) {
        return ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations().get("/**");
    }

    @Test
    void should_includeLocalhost_when_profileIsNotProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});

        CorsConfigurationSource source = aSecurityConfig().corsConfigurationSource(environment);

        assertThat(resolveConfig(source).getAllowedOriginPatterns()).contains("http://localhost:*");
    }

    @Test
    void should_excludeLocalhost_when_profileIsProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        CorsConfigurationSource source = aSecurityConfig().corsConfigurationSource(environment);

        assertThat(resolveConfig(source).getAllowedOriginPatterns()).doesNotContain("http://localhost:*");
    }

    @Test
    void should_alwaysIncludeConfiguredOrigins_regardlessOfProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        CorsConfigurationSource source = aSecurityConfig().corsConfigurationSource(environment);

        assertThat(resolveConfig(source).getAllowedOriginPatterns())
            .contains("https://vetly.example.com", "https://app.vetly.example.com");
    }
}
