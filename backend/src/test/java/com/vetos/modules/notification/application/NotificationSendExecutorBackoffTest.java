package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationSendPort;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NotificationSendExecutorBackoffTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationSendPort notificationSendPort;
    @Mock private TenantLookupPort tenantLookupPort;

    @Test
    void should_scheduleShortBackoff_when_firstFailure() {
        NotificationSendExecutor executor =
            new NotificationSendExecutor(notificationLogRepository, notificationSendPort, tenantLookupPort);
        Instant before = Instant.now();

        Instant nextRetry = executor.computeNextRetryAt(1);

        assertThat(nextRetry).isAfter(before.plusSeconds(60)).isBefore(before.plusSeconds(180));
    }

    @Test
    void should_scheduleLongBackoff_when_fourthFailure() {
        NotificationSendExecutor executor =
            new NotificationSendExecutor(notificationLogRepository, notificationSendPort, tenantLookupPort);
        Instant before = Instant.now();

        Instant nextRetry = executor.computeNextRetryAt(4);

        assertThat(nextRetry).isAfter(before.plusSeconds(3 * 3600)).isBefore(before.plusSeconds(9 * 3600));
    }

    @Test
    void should_returnNull_when_fifthFailure_automaticRetriesExhausted() {
        NotificationSendExecutor executor =
            new NotificationSendExecutor(notificationLogRepository, notificationSendPort, tenantLookupPort);

        Instant nextRetry = executor.computeNextRetryAt(5);

        assertThat(nextRetry).isNull();
    }
}
