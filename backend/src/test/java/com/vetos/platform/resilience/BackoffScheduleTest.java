package com.vetos.platform.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffScheduleTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    void should_followOutboxSchedule_thenStop() {
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(1, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(2, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(3, NOW)).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(4, NOW)).isEqualTo(NOW.plus(Duration.ofHours(6)));
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(5, NOW)).isNull();
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(9, NOW)).isNull();
    }

    @Test
    void should_returnNull_forNonPositiveAttempt() {
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(0, NOW)).isNull();
    }
}
