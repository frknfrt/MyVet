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
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(mock(StaffSessionRevocations.class), jwtTokenProvider);

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

    private static org.springframework.mock.web.MockHttpServletRequest cookieRequest(String method, String token) {
        var request = new org.springframework.mock.web.MockHttpServletRequest(method, "/api/v1/patients");
        request.setCookies(new jakarta.servlet.http.Cookie(SessionCookies.NAME, token));
        return request;
    }

    @Test
    void should_authenticateFromSessionCookie_onRead() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), "VET");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        AtomicReference<Object> principal = new AtomicReference<>();
        FilterChain chain = (req, res) -> principal.set(SecurityContextHolder.getContext().getAuthentication().getPrincipal());

        filter.doFilter(cookieRequest("GET", token), response, chain);

        assertThat(principal.get()).isInstanceOf(AuthenticatedStaffUser.class);
        assertThat(((AuthenticatedStaffUser) principal.get()).tenantId()).isEqualTo(tenantId);
    }

    @Test
    void should_rejectCookieWrite_withoutClientHeader() throws Exception {
        String token = jwtTokenProvider.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), "VET");
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        AtomicReference<Boolean> reached = new AtomicReference<>(false);

        filter.doFilter(cookieRequest("POST", token), response, (req, res) -> reached.set(true));

        assertThat(reached.get()).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_CHECK_FAILED");
    }

    @Test
    void should_allowCookieWrite_withClientHeader() throws Exception {
        String token = jwtTokenProvider.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), "VET");
        var request = cookieRequest("PUT", token);
        request.addHeader(SessionCookies.CLIENT_HEADER, "web");
        AtomicReference<Boolean> authenticated = new AtomicReference<>(false);

        filter.doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(),
            (req, res) -> authenticated.set(SecurityContextHolder.getContext().getAuthentication() != null));

        assertThat(authenticated.get()).isTrue();
    }

    @Test
    void should_allowBearerWrite_withoutClientHeader() throws Exception {
        String token = jwtTokenProvider.generateToken(UUID.randomUUID(), UUID.randomUUID(), List.of(), "VET");
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/v1/patients");
        request.addHeader("Authorization", "Bearer " + token);
        AtomicReference<Boolean> authenticated = new AtomicReference<>(false);

        filter.doFilter(request, new org.springframework.mock.web.MockHttpServletResponse(),
            (req, res) -> authenticated.set(SecurityContextHolder.getContext().getAuthentication() != null));

        assertThat(authenticated.get()).isTrue();
    }
}
