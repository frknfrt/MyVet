package com.vetos.platform.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Canli docker-compose Postgres gerekir. yml anahtarlari yanlis yazilirsa sessizce varsayilana duser -- burada tek tek dogrulanir. */
@SpringBootTest
class ResilienceWiringTest {

    @Autowired private CircuitBreakerRegistry circuitBreakers;
    @Autowired private BulkheadRegistry bulkheads;
    @Autowired private RetryRegistry retries;
    @Autowired private MeterRegistry meters;

    @Test
    void should_configureCircuitBreakers_fromYaml() {
        Map<String, Duration> slow = Map.of("ai", Duration.ofSeconds(30), "payment", Duration.ofSeconds(10), "efatura", Duration.ofSeconds(20));
        slow.forEach((name, threshold) -> {
            CircuitBreakerConfig c = circuitBreakers.circuitBreaker(name).getCircuitBreakerConfig();
            assertThat(c.getSlidingWindowType()).as(name).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
            assertThat(c.getSlidingWindowSize()).as(name).isEqualTo(10);
            assertThat(c.getMinimumNumberOfCalls()).as(name).isEqualTo(5);
            assertThat(c.getFailureRateThreshold()).as(name).isEqualTo(50f);
            assertThat(c.getSlowCallRateThreshold()).as(name).isEqualTo(50f);
            assertThat(c.getSlowCallDurationThreshold()).as(name).isEqualTo(threshold);
            assertThat(c.getPermittedNumberOfCallsInHalfOpenState()).as(name).isEqualTo(2);
            assertThat(c.isAutomaticTransitionFromOpenToHalfOpenEnabled()).as(name).isFalse();
            assertThat(c.getRecordExceptionPredicate().test(new ResourceAccessException("x"))).as(name).isTrue();
            assertThat(c.getRecordExceptionPredicate().test(new IllegalStateException("x"))).as(name).isFalse();
        });
        assertThat(circuitBreakers.getAllCircuitBreakers()).extracting(cb -> cb.getName()).contains("ai", "payment", "efatura");
    }

    @Test
    void should_configureAiBulkhead() {
        var c = bulkheads.bulkhead("ai").getBulkheadConfig();
        assertThat(c.getMaxConcurrentCalls()).isEqualTo(4);
        assertThat(c.getMaxWaitDuration()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void should_configureReadRetries() {
        for (String name : new String[] {"payment-read", "efatura-read"}) {
            var c = retries.retry(name).getRetryConfig();
            assertThat(c.getMaxAttempts()).as(name).isEqualTo(3);
            assertThat(c.getExceptionPredicate().test(new ResourceAccessException("x"))).as(name).isTrue();
            assertThat(c.getExceptionPredicate().test(new IllegalStateException("x"))).as(name).isFalse();
        }
    }

    @Test
    void should_exposeMetrics() {
        assertThat(meters.find("resilience4j.circuitbreaker.state").tag("name", "ai").meters()).isNotEmpty();
        assertThat(meters.find("resilience4j.bulkhead.available.concurrent.calls").tag("name", "ai").meters()).isNotEmpty();
    }
}
