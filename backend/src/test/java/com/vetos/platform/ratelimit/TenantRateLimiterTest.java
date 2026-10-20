package com.vetos.platform.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantRateLimiterTest {

    private final TenantRateLimiter limiter = new TenantRateLimiter(new RateLimitProperties(3, 2, 100));

    @Test
    void should_allowUpToCapacity_then_rejectWithRetryAfter() {
        UUID tenant = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume(TenantPool.GENERAL, tenant, 1).consumed()).isTrue();
        }
        ConsumeResult rejected = limiter.tryConsume(TenantPool.GENERAL, tenant, 1);
        assertThat(rejected.consumed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void should_keepTenantsAndPoolsSeparate() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        limiter.tryConsume(TenantPool.AI, a, 2);
        assertThat(limiter.tryConsume(TenantPool.AI, a, 1).consumed()).isFalse();
        assertThat(limiter.tryConsume(TenantPool.AI, b, 1).consumed()).isTrue();
        assertThat(limiter.tryConsume(TenantPool.GENERAL, a, 1).consumed()).isTrue();
    }

    @Test
    void should_rejectWithFiniteRetry_when_tokensExceedCapacity() {
        ConsumeResult result = limiter.tryConsume(TenantPool.CAMPAIGN_RECIPIENTS, UUID.randomUUID(), 101);
        assertThat(result.consumed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(3600L);
    }

    @Test
    void should_consumeNothing_when_tokensZero() {
        UUID tenant = UUID.randomUUID();
        assertThat(limiter.tryConsume(TenantPool.AI, tenant, 0).consumed()).isTrue();
        assertThat(limiter.tryConsume(TenantPool.AI, tenant, 2).consumed()).isTrue();
    }

    @Test
    void should_refundTokens() {
        UUID tenant = UUID.randomUUID();
        limiter.tryConsume(TenantPool.CAMPAIGN_RECIPIENTS, tenant, 100);
        limiter.refund(TenantPool.CAMPAIGN_RECIPIENTS, tenant, 40);
        assertThat(limiter.tryConsume(TenantPool.CAMPAIGN_RECIPIENTS, tenant, 40).consumed()).isTrue();
        assertThat(limiter.tryConsume(TenantPool.CAMPAIGN_RECIPIENTS, tenant, 1).consumed()).isFalse();
    }

    @Test
    void should_throwWithRetryAfter_on_consumeOrThrow() {
        UUID tenant = UUID.randomUUID();
        limiter.consumeOrThrow(TenantPool.AI, tenant, 2);
        assertThatThrownBy(() -> limiter.consumeOrThrow(TenantPool.AI, tenant, 1))
            .isInstanceOf(RateLimitExceededException.class)
            .satisfies(ex -> {
                RateLimitExceededException e = (RateLimitExceededException) ex;
                assertThat(e.getErrorCode()).isEqualTo("RATE_LIMITED");
                assertThat(e.getRetryAfterSeconds()).isPositive();
                assertThat(e.getMessage()).startsWith("Çok fazla istek gönderildi, lütfen ").endsWith(" saniye sonra tekrar deneyin.");
            });
    }

    @Test
    void should_roundRetrySecondsUp_minimumOne() {
        assertThat(TenantRateLimiter.toRetrySeconds(0)).isEqualTo(1);
        assertThat(TenantRateLimiter.toRetrySeconds(1)).isEqualTo(1);
        assertThat(TenantRateLimiter.toRetrySeconds(1_000_000_001L)).isEqualTo(2);
    }
}
