package com.vetos.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationOutcomesTest {

    /** Testte ilerletilebilen saat. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-14T10:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock();
    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(registry, clock);

    private double calls(String integration, String outcome) {
        Counter c = registry.find("vetly.integration.calls").tag("integration", integration).tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void should_beUp_when_noAttempts() {
        assertThat(outcomes.health(IntegrationOutcomes.EFATURA).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_beDown_when_threeFailuresAndNoSuccess() {
        outcomes.record(IntegrationOutcomes.SMS, false);
        outcomes.record(IntegrationOutcomes.SMS, false);
        assertThat(outcomes.health(IntegrationOutcomes.SMS).getStatus()).isEqualTo(Status.UP);

        outcomes.record(IntegrationOutcomes.SMS, false);
        assertThat(outcomes.health(IntegrationOutcomes.SMS).getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void should_beUp_when_anySuccessInWindow() {
        outcomes.record(IntegrationOutcomes.SMS, true);
        outcomes.record(IntegrationOutcomes.SMS, false);
        outcomes.record(IntegrationOutcomes.SMS, false);
        outcomes.record(IntegrationOutcomes.SMS, false);

        assertThat(outcomes.health(IntegrationOutcomes.SMS).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_forgetAttemptsOlderThanThirtyMinutes() {
        outcomes.record(IntegrationOutcomes.PAYMENT, true);
        clock.advance(Duration.ofMinutes(31));
        outcomes.record(IntegrationOutcomes.PAYMENT, false);
        outcomes.record(IntegrationOutcomes.PAYMENT, false);
        outcomes.record(IntegrationOutcomes.PAYMENT, false);

        assertThat(outcomes.health(IntegrationOutcomes.PAYMENT).getStatus()).isEqualTo(Status.DOWN);

        clock.advance(Duration.ofMinutes(31));
        assertThat(outcomes.health(IntegrationOutcomes.PAYMENT).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_keepAtMostThousandAttempts() {
        outcomes.record(IntegrationOutcomes.AI, true);
        for (int i = 0; i < 1000; i++) {
            outcomes.record(IntegrationOutcomes.AI, false);
        }
        // tek basari 1000 sinirinin disina itildi
        assertThat(outcomes.health(IntegrationOutcomes.AI).getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void should_countCallsWithIntegrationAndOutcomeTags() {
        outcomes.record(IntegrationOutcomes.WHATSAPP, true);
        outcomes.record(IntegrationOutcomes.WHATSAPP, false);
        outcomes.record(IntegrationOutcomes.TARBIL, true);

        assertThat(calls("whatsapp", "success")).isEqualTo(1);
        assertThat(calls("whatsapp", "failure")).isEqualTo(1);
        assertThat(calls("tarbil", "success")).isEqualTo(1);
    }

    @Test
    void should_timeAiCallAndRecordOutcome() throws Exception {
        String result = outcomes.recordAiCall("soap", "claude", () -> "ok");
        assertThatThrownBy(() -> outcomes.recordAiCall("soap", "claude", () -> { throw new IllegalStateException("x"); }))
            .isInstanceOf(IllegalStateException.class);

        assertThat(result).isEqualTo("ok");
        Timer timer = registry.find("vetly.ai.duration").tag("task", "soap").tag("provider", "claude").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(calls("ai", "success")).isEqualTo(1);
        assertThat(calls("ai", "failure")).isEqualTo(1);
    }

    @Test
    void should_classifyOutageStatuses() {
        assertThat(IntegrationOutcomes.isOutageStatus(500)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(503)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(401)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(402)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(403)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(429)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(400)).isFalse();
        assertThat(IntegrationOutcomes.isOutageStatus(404)).isFalse();
        assertThat(IntegrationOutcomes.isOutageStatus(422)).isFalse();
    }

    @Test
    void should_notLoseRecords_when_calledConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    outcomes.record(IntegrationOutcomes.EFATURA, false);
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(calls("efatura", "failure")).isEqualTo(800);
        assertThat(outcomes.health(IntegrationOutcomes.EFATURA).getDetails()).containsEntry("attempts", 800);
    }
}
