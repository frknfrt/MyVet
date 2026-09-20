package com.vetos.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitFilterTest {

    private final RateLimitFilter filter = new RateLimitFilter();

    @Test
    void should_passThrough_when_pathNotAuthOrPublic() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/notifications/status");
        when(request.getContextPath()).thenReturn("");

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(response, never()).setStatus(anyInt());
    }

    @Test
    void should_allowRequests_withinAuthLimit() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getContextPath()).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(10)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void should_reject_when_authLimitExceeded() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getContextPath()).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("10.0.0.2");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }
        filter.doFilter(request, response, chain);

        verify(chain, times(10)).doFilter(request, response);
        verify(response).setStatus(429);
        verify(response).setContentType("application/json");
        assertThat(body.toString()).contains("RATE_LIMITED");
    }

    @Test
    void should_ignoreXForwardedFor_and_useRemoteAddr() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getContextPath()).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("10.0.0.99");
        lenient().when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5");

        filter.doFilter(request, response, chain);

        verify(request).getRemoteAddr();
        verify(chain).doFilter(request, response);
    }

    @Test
    void should_applyRateLimit_when_authPathIsPercentEncoded() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        // "%61" decodes to "a" -> "/api/v1/%61uth/login" decodes to "/api/v1/auth/login"
        when(request.getRequestURI()).thenReturn("/api/v1/%61uth/login");
        when(request.getContextPath()).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("10.0.0.50");
        StringWriter body = new StringWriter();
        lenient().when(response.getWriter()).thenReturn(new PrintWriter(body));

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }
        filter.doFilter(request, response, chain);

        verify(chain, times(10)).doFilter(request, response);
        verify(response).setStatus(429);
    }

    @Test
    void should_trackDifferentIps_independently() throws ServletException, IOException {
        HttpServletRequest requestA = mock(HttpServletRequest.class);
        HttpServletRequest requestB = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(requestA.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(requestA.getContextPath()).thenReturn("");
        when(requestA.getRemoteAddr()).thenReturn("10.0.0.10");
        when(requestB.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(requestB.getContextPath()).thenReturn("");
        when(requestB.getRemoteAddr()).thenReturn("10.0.0.20");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(requestA, response, chain);
        }
        filter.doFilter(requestB, response, chain);

        verify(response, never()).setStatus(429);
    }

    @Test
    void should_allowRequests_withinPlatformAdminAuthLimit() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/platform-admin/auth/login");
        when(request.getContextPath()).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("10.0.0.40");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(10)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }

    @Test
    void should_reject_when_platformAdminAuthLimitExceeded() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/platform-admin/auth/login");
        when(request.getContextPath()).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("10.0.0.41");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));

        for (int i = 0; i < 10; i++) {
            filter.doFilter(request, response, chain);
        }
        filter.doFilter(request, response, chain);

        verify(chain, times(10)).doFilter(request, response);
        verify(response).setStatus(429);
        assertThat(body.toString()).contains("RATE_LIMITED");
    }

    @Test
    void should_trackAuthAndPlatformAdminAuthBuckets_independently_forSameIp() throws ServletException, IOException {
        HttpServletRequest authRequest = mock(HttpServletRequest.class);
        HttpServletRequest platformAdminRequest = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(authRequest.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(authRequest.getContextPath()).thenReturn("");
        when(authRequest.getRemoteAddr()).thenReturn("10.0.0.42");
        when(platformAdminRequest.getRequestURI()).thenReturn("/api/v1/platform-admin/auth/login");
        when(platformAdminRequest.getContextPath()).thenReturn("");
        when(platformAdminRequest.getRemoteAddr()).thenReturn("10.0.0.42");

        for (int i = 0; i < 10; i++) {
            filter.doFilter(authRequest, response, chain);
        }
        filter.doFilter(platformAdminRequest, response, chain);

        verify(response, never()).setStatus(429);
    }

    @Test
    void should_allowMoreRequests_forPublicPath_thanAuthPath() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/public/appointments");
        when(request.getContextPath()).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("10.0.0.30");

        for (int i = 0; i < 60; i++) {
            filter.doFilter(request, response, chain);
        }

        verify(chain, times(60)).doFilter(request, response);
        verify(response, never()).setStatus(429);
    }
}
