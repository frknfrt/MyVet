package com.vetos.platform.web;

import com.vetos.platform.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerRateLimitTest {

    @Test
    void should_return429WithRetryAfter_for_rateLimitExceeded() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/api/v1/notifications/campaigns/send");

        var response = handler.handleRateLimited(new RateLimitExceededException(42), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
        assertThat(response.getBody().errorCode()).isEqualTo("RATE_LIMITED");
        assertThat(response.getBody().message()).contains("42 saniye");
    }

    @Test
    void should_return429WithoutRetryAfter_for_monthlyQuota() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/api/v1/ai/soap-drafts");

        var response = handler.handleMonthlyQuota(
            new com.vetos.platform.quota.MonthlyQuotaExceededException(com.vetos.platform.quota.UsageKind.AI, 30, 30, 1), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().containsKey("Retry-After")).isFalse();
        assertThat(response.getBody().errorCode()).isEqualTo("MONTHLY_QUOTA_EXCEEDED");
    }
}
