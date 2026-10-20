package com.vetos.platform.web;

import com.vetos.platform.ratelimit.RateLimitProperties;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TenantRateLimitFilterTest {

    private final TenantRateLimiter limiter = new TenantRateLimiter(new RateLimitProperties(3, 1, 2000));
    private final TenantRateLimitFilter filter = new TenantRateLimitFilter(limiter);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private MockHttpServletResponse call(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    @Test
    void should_passThrough_when_noTenant() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("GET", "/api/v1/platform-admin/plans").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void should_rejectWith429_when_generalLimitExceeded() throws Exception {
        TenantContext.set(UUID.randomUUID());
        for (int i = 0; i < 3; i++) {
            assertThat(call("GET", "/api/v1/patients").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse rejected = call("GET", "/api/v1/patients");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(rejected.getHeader("Retry-After"))).isPositive();
        assertThat(rejected.getContentAsString()).contains("\"errorCode\":\"RATE_LIMITED\"");
    }

    @Test
    void should_applyAiLimit_onlyToGenerationPosts() throws Exception {
        TenantContext.set(UUID.randomUUID());
        assertThat(call("POST", "/api/v1/ai/diagnosis-suggestions").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/ai/diagnosis-suggestions/" + UUID.randomUUID() + "/decision").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/ai/soap-drafts").getStatus()).isEqualTo(429);
    }

    @Test
    void should_notConsumeAiToken_when_generalLimitRejects() throws Exception {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        for (int i = 0; i < 3; i++) {
            call("GET", "/api/v1/patients");
        }
        assertThat(call("POST", "/api/v1/ai/soap-drafts").getStatus()).isEqualTo(429);
        assertThat(limiter.tryConsume(com.vetos.platform.ratelimit.TenantPool.AI, tenant, 1).consumed()).isTrue();
    }

    @Test
    void should_coverAllThreeGenerationEndpoints() {
        assertThat(TenantRateLimitFilter.AI_GENERATION_PATHS).containsExactlyInAnyOrder(
            "/api/v1/ai/soap-drafts", "/api/v1/ai/treatment-recommendations", "/api/v1/ai/diagnosis-suggestions"
        );
    }
}
