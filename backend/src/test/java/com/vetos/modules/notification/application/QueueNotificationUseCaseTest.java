package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationType;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.UUID;

import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class QueueNotificationUseCaseTest {

    private static final UUID TENANT = UUID.randomUUID();

    private final NotificationLogRepository repository = mock(NotificationLogRepository.class);
    private final NotificationSendExecutor executor = mock(NotificationSendExecutor.class);
    private final UsageQuotaPort quota = mock(UsageQuotaPort.class);
    private final QueueNotificationUseCase useCase = new QueueNotificationUseCase(repository, executor, quota);

    QueueNotificationUseCaseTest() {
        when(repository.save(any())).then(returnsFirstArg());
    }

    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"APPOINTMENT_CONFIRMATION", "APPOINTMENT_REMINDER", "VACCINATION_REMINDER"})
    void should_countAutomaticMessages_unconditionally(NotificationType type) {
        useCase.execute(TENANT, UUID.randomUUID(), null, NotificationChannel.SMS, type, "05xx", "mesaj", null, "Ali");
        verify(quota).recordUnconditional(TENANT, UsageKind.MESSAGE, 1);
    }

    @Test
    void should_notCountCampaignMessages_again() {
        useCase.execute(TENANT, UUID.randomUUID(), null, NotificationChannel.SMS, NotificationType.CAMPAIGN_MESSAGE, "05xx", "m", null, "Ali");
        verifyNoInteractions(quota);
    }

    @Test
    void should_stillQueue_when_unconditionalCountFails() {
        doThrow(new IllegalStateException("db")).when(quota).recordUnconditional(TENANT, UsageKind.MESSAGE, 1);

        useCase.execute(TENANT, UUID.randomUUID(), null, NotificationChannel.SMS, NotificationType.APPOINTMENT_REMINDER, "05xx", "m", null, "Ali");

        verify(repository).save(any());
    }
}
