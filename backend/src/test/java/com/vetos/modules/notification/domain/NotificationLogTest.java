package com.vetos.modules.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationLogTest {

    private NotificationLog aLog() {
        return NotificationLog.queue(
            UUID.randomUUID(), UUID.randomUUID(), null, NotificationChannel.SMS, NotificationType.APPOINTMENT_REMINDER,
            "05551234567", "mesaj", null, null
        );
    }

    @Test
    void should_incrementAttemptCount_and_setNextRetryAt_when_markFailed() {
        NotificationLog log = aLog();
        Instant nextRetry = Instant.now().plusSeconds(120);

        log.markFailed(nextRetry);

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(log.getAttemptCount()).isEqualTo(1);
        assertThat(log.getNextRetryAt()).isEqualTo(nextRetry);
    }

    @Test
    void should_accumulateAttemptCount_across_multipleFailures() {
        NotificationLog log = aLog();

        log.markFailed(Instant.now().plusSeconds(120));
        log.markFailed(Instant.now().plusSeconds(600));

        assertThat(log.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void should_allowNullNextRetryAt_when_automaticRetriesExhausted() {
        NotificationLog log = aLog();

        log.markFailed(null);

        assertThat(log.getNextRetryAt()).isNull();
        assertThat(log.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void should_clearNextRetryAt_when_markRetrying() {
        NotificationLog log = aLog();
        log.markFailed(Instant.now().plusSeconds(120));

        log.markRetrying();

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(log.getNextRetryAt()).isNull();
    }
}
