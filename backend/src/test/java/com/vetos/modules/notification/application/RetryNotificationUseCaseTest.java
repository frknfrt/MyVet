package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.modules.notification.domain.exception.NotificationLogNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * NotificationLog bilincli olarak @TenantId DISINDA (tasarim dokumani S6).
 * Tek gercek risk POST /notifications/logs/{id}/retry -- id istemciden
 * geliyor. Baska kiracinin kaydi 404 ile "yok" gibi gorunmeli, var oldugu
 * bile sizdirilmamali.
 */
@ExtendWith(MockitoExtension.class)
class RetryNotificationUseCaseTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationSendExecutor notificationSendExecutor;

    private NotificationLog aLog(UUID tenantId, UUID logId) {
        NotificationLog log = NotificationLog.queue(
            tenantId, UUID.randomUUID(), null, NotificationChannel.SMS, NotificationType.APPOINTMENT_REMINDER,
            "05551234567", "mesaj", null, null
        );
        ReflectionTestUtils.setField(log, "id", logId);
        return log;
    }

    @Test
    void should_throwNotFound_when_logBelongsToAnotherTenant() {
        RetryNotificationUseCase useCase =
            new RetryNotificationUseCase(notificationLogRepository, notificationSendExecutor);
        UUID callerTenantId = UUID.randomUUID();
        UUID foreignTenantId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        when(notificationLogRepository.findById(logId)).thenReturn(Optional.of(aLog(foreignTenantId, logId)));

        assertThatThrownBy(() -> useCase.execute(callerTenantId, logId))
            .isInstanceOf(NotificationLogNotFoundException.class);

        verify(notificationLogRepository, never()).save(any());
        verify(notificationSendExecutor, never()).attemptSend(any());
    }

    @Test
    void should_retry_when_logBelongsToCallerTenant() {
        RetryNotificationUseCase useCase =
            new RetryNotificationUseCase(notificationLogRepository, notificationSendExecutor);
        UUID tenantId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        NotificationLog log = aLog(tenantId, logId);
        when(notificationLogRepository.findById(logId)).thenReturn(Optional.of(log));

        useCase.execute(tenantId, logId);

        verify(notificationLogRepository).save(log);
        verify(notificationSendExecutor).attemptSend(logId);
    }
}
