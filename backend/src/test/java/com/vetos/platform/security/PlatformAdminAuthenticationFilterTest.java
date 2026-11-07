package com.vetos.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformAdminAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";
    private final PlatformAdminJwtTokenProvider provider = new PlatformAdminJwtTokenProvider(TEST_SECRET, 480);
    private final PlatformAdminAuthenticationFilter filter = new PlatformAdminAuthenticationFilter(provider);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletRequest cookieRequest(String method, String uri) {
        var request = new MockHttpServletRequest(method, uri);
        request.setCookies(new Cookie(SessionCookies.ADMIN_NAME, provider.generateToken(UUID.randomUUID(), "admin@vetly.test")));
        return request;
    }

    private Object principalAfter(MockHttpServletRequest request, MockHttpServletResponse response) throws Exception {
        AtomicReference<Object> principal = new AtomicReference<>();
        FilterChain chain = (req, res) -> {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            principal.set(auth == null ? null : auth.getPrincipal());
        };
        filter.doFilter(request, response, chain);
        return principal.get();
    }

    @Test
    void should_authenticateFromAdminCookie_onRead() throws Exception {
        Object principal = principalAfter(cookieRequest("GET", "/api/v1/platform-admin/tenants"), new MockHttpServletResponse());

        assertThat(principal).isInstanceOf(AuthenticatedPlatformAdmin.class);
        assertThat(((AuthenticatedPlatformAdmin) principal).email()).isEqualTo("admin@vetly.test");
    }

    @Test
    void should_rejectCookieWrite_withoutClientHeader() throws Exception {
        var response = new MockHttpServletResponse();

        Object principal = principalAfter(cookieRequest("PUT", "/api/v1/platform-admin/plans/x"), response);

        assertThat(principal).isNull();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("CSRF_CHECK_FAILED");
    }

    @Test
    void should_allowCookieWrite_withClientHeader() throws Exception {
        var request = cookieRequest("PUT", "/api/v1/platform-admin/plans/x");
        request.addHeader(SessionCookies.CLIENT_HEADER, "web");

        assertThat(principalAfter(request, new MockHttpServletResponse())).isInstanceOf(AuthenticatedPlatformAdmin.class);
    }

    @Test
    void should_ignoreClinicCookie() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/platform-admin/tenants");
        request.setCookies(new Cookie(SessionCookies.NAME, provider.generateToken(UUID.randomUUID(), "x@y.z")));

        assertThat(principalAfter(request, new MockHttpServletResponse())).isNull();
    }

    @Test
    void should_skipLoginEndpoint_evenWithStaleCookie() throws Exception {
        var response = new MockHttpServletResponse();
        AtomicReference<Boolean> reached = new AtomicReference<>(false);

        filter.doFilter(cookieRequest("POST", "/api/v1/platform-admin/auth/login"), response, (req, res) -> reached.set(true));

        assertThat(reached.get()).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }
}
