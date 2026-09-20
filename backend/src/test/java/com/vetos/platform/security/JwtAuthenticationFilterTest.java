package com.vetos.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, 480);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void should_putTenantIdInMdc_duringFilterChain_when_tokenValid() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), "ADMIN");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        AtomicReference<String> seenDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenDuringChain.set(MDC.get("tenantId"));

        filter.doFilter(request, response, chain);

        assertThat(seenDuringChain.get()).isEqualTo(tenantId.toString());
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test
    void should_notPutTenantIdInMdc_when_noToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        AtomicReference<String> seenDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenDuringChain.set(MDC.get("tenantId"));

        filter.doFilter(request, response, chain);

        assertThat(seenDuringChain.get()).isNull();
    }
}
