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
class PlatformAdminSecurityConfigCorsTest {

    @Mock private PlatformAdminJwtTokenProvider platformAdminJwtTokenProvider;
    @Mock private JsonAuthenticationEntryPoint authenticationEntryPoint;
    @Mock private JsonAccessDeniedHandler accessDeniedHandler;
    @Mock private Environment environment;

    private PlatformAdminSecurityConfig aPlatformAdminSecurityConfig() {
        PlatformAdminSecurityConfig config = new PlatformAdminSecurityConfig(
            platformAdminJwtTokenProvider, authenticationEntryPoint, accessDeniedHandler
        );
        ReflectionTestUtils.setField(config, "frontendBaseUrl", "https://app.vetly.example.com");
        return config;
    }

    private CorsConfiguration resolveConfig(CorsConfigurationSource source) {
        return ((UrlBasedCorsConfigurationSource) source).getCorsConfigurations().get("/**");
    }

    @Test
    void should_includeLocalhost_when_profileIsNotProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{});

        CorsConfigurationSource source = aPlatformAdminSecurityConfig().corsConfigurationSource(environment);

        assertThat(resolveConfig(source).getAllowedOriginPatterns()).contains("http://localhost:*");
    }

    @Test
    void should_excludeLocalhost_when_profileIsProd() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        CorsConfigurationSource source = aPlatformAdminSecurityConfig().corsConfigurationSource(environment);

        assertThat(resolveConfig(source).getAllowedOriginPatterns()).doesNotContain("http://localhost:*");
    }

    @Test
    void should_alwaysIncludeConfiguredOrigin_regardlessOfProfile() {
        when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});

        CorsConfigurationSource source = aPlatformAdminSecurityConfig().corsConfigurationSource(environment);

        assertThat(resolveConfig(source).getAllowedOriginPatterns()).contains("https://app.vetly.example.com");
    }
}
