package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryDueNotificationsUseCaseTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationSendExecutor notificationSendExecutor;

    private NotificationLog aFailedLog() {
        NotificationLog log = NotificationLog.queue(
            UUID.randomUUID(), UUID.randomUUID(), null, NotificationChannel.SMS, NotificationType.APPOINTMENT_REMINDER,
            "05551234567", "mesaj", null, null
        );
        log.markFailed(Instant.now());
        return log;
    }

    @Test
    void should_markClaimedLogsRetrying_and_returnClaimedCount() {
        RetryDueNotificationsUseCase useCase =
            new RetryDueNotificationsUseCase(notificationLogRepository, notificationSendExecutor);
        NotificationLog log1 = aFailedLog();
        NotificationLog log2 = aFailedLog();
        when(notificationLogRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of(log1, log2));

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isEqualTo(2);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(l ->
            assertThat(l.getStatus()).isEqualTo(com.vetos.modules.notification.domain.NotificationStatus.PENDING));
    }

    @Test
    void should_returnZero_when_nothingDue() {
        RetryDueNotificationsUseCase useCase =
            new RetryDueNotificationsUseCase(notificationLogRepository, notificationSendExecutor);
        when(notificationLogRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of());

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isZero();
        verify(notificationLogRepository, never()).save(any());
    }
}
