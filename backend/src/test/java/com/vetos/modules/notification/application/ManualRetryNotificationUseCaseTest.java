package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationStatus;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.modules.notification.domain.exception.NotificationLogNotFoundException;
import com.vetos.modules.notification.domain.exception.NotificationLogNotRetryableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ManualRetryNotificationUseCaseTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationSendExecutor notificationSendExecutor;

    private NotificationLog aLog() {
        return NotificationLog.queue(
            UUID.randomUUID(), UUID.randomUUID(), null, NotificationChannel.SMS, NotificationType.APPOINTMENT_REMINDER,
            "05551234567", "mesaj", null, null
        );
    }

    @Test
    void should_markPendingAndReattempt_when_logFailed() {
        ManualRetryNotificationUseCase useCase =
            new ManualRetryNotificationUseCase(notificationLogRepository, notificationSendExecutor);
        NotificationLog log = aLog();
        log.markFailed("onceki hata", null);
        UUID logId = UUID.randomUUID();
        when(notificationLogRepository.findById(logId)).thenReturn(Optional.of(log));

        useCase.execute(logId);

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(log.getFailureReason()).isNull();
        verify(notificationLogRepository).save(log);
        verify(notificationSendExecutor).attemptSend(logId);
    }

    @Test
    void should_throwNotFound_when_logMissing() {
        ManualRetryNotificationUseCase useCase =
            new ManualRetryNotificationUseCase(notificationLogRepository, notificationSendExecutor);
        UUID logId = UUID.randomUUID();
        when(notificationLogRepository.findById(logId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.execute(logId)).isInstanceOf(NotificationLogNotFoundException.class);

        verify(notificationLogRepository, never()).save(any());
        verifyNoInteractions(notificationSendExecutor);
    }

    @Test
    void should_throwNotRetryable_when_logNotFailed() {
        ManualRetryNotificationUseCase useCase =
            new ManualRetryNotificationUseCase(notificationLogRepository, notificationSendExecutor);
        NotificationLog log = aLog();
        log.markSent();
        UUID logId = UUID.randomUUID();
        when(notificationLogRepository.findById(logId)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> useCase.execute(logId)).isInstanceOf(NotificationLogNotRetryableException.class);

        verify(notificationLogRepository, never()).save(any());
        verifyNoInteractions(notificationSendExecutor);
    }
}
