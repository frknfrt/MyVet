package com.vetos.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void should_putRequestIdInMdc_duringFilterChain_and_clearAfterwards() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> seenDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenDuringChain.set(MDC.get("requestId"));

        filter.doFilter(request, response, chain);

        assertThat(seenDuringChain.get()).isNotNull().isNotBlank();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void should_generateDifferentRequestId_forEachRequest() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> first.set(MDC.get("requestId")));
        filter.doFilter(request, response, (req, res) -> second.set(MDC.get("requestId")));

        assertThat(first.get()).isNotEqualTo(second.get());
    }

    @Test
    void should_clearMdc_evenWhenChainThrows() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = (req, res) -> { throw new ServletException("boom"); };

        try {
            filter.doFilter(request, response, chain);
        } catch (ServletException | IOException ignored) {
            // beklenen
        }

        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void should_reuseIncomingRequestId_when_safe() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("X-Request-Id")).thenReturn("frontend-1234abcd");
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get("requestId")));

        assertThat(seen.get()).isEqualTo("frontend-1234abcd");
        verify(response).setHeader("X-Request-Id", "frontend-1234abcd");
    }

    @Test
    void should_ignoreIncomingRequestId_when_unsafe() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> seen = new AtomicReference<>();

        for (String unsafe : new String[]{"abc12345\nSAHTE LOG SATIRI", "x".repeat(10_000), "kisa"}) {
            when(request.getHeader("X-Request-Id")).thenReturn(unsafe);
            filter.doFilter(request, response, (req, res) -> seen.set(MDC.get("requestId")));
            assertThat(seen.get()).isNotEqualTo(unsafe).matches("[0-9a-f-]{36}");
        }
    }

    @Test
    void should_writeGeneratedRequestIdToResponseHeader() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> seen = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> seen.set(MDC.get("requestId")));

        verify(response).setHeader("X-Request-Id", seen.get());
    }
}
