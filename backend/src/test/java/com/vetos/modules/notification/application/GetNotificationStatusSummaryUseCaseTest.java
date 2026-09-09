package com.vetos.modules.notification.application;

import com.vetos.modules.notification.application.dto.NotificationStatusSummary;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationSendPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Ayarlar > SMS/WhatsApp ekranindaki rozetler daha once tek bir "connected"
 * bayragina dayaniyordu (en az bir kanal gercekse true) -- bu yuzden SMS
 * (Ileti Merkezi) gercekten baglandiktan sonra bile arayuz hala "SMS mock"
 * diyordu, cunku WhatsApp (Twilio) zaten gercekti ve tek bayrak zaten true'ydu.
 * Bu test iki kanalin BAGIMSIZ raporlandigini dogrular.
 */
@ExtendWith(MockitoExtension.class)
class GetNotificationStatusSummaryUseCaseTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationSendPort notificationSendPort;

    private GetNotificationStatusSummaryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetNotificationStatusSummaryUseCase(notificationLogRepository, notificationSendPort);
    }

    @Test
    void should_reportSmsConfiguredIndependentlyFromWhatsapp_when_onlySmsIsReal() {
        UUID tenantId = UUID.randomUUID();
        when(notificationLogRepository.findByTenantId(tenantId)).thenReturn(List.of());
        when(notificationSendPort.isSmsConfigured()).thenReturn(true);
        when(notificationSendPort.isWhatsappConfigured()).thenReturn(false);

        NotificationStatusSummary summary = useCase.execute(tenantId);

        assertThat(summary.smsConfigured()).isTrue();
        assertThat(summary.whatsappConfigured()).isFalse();
    }

    @Test
    void should_reportWhatsappConfiguredIndependentlyFromSms_when_onlyWhatsappIsReal() {
        UUID tenantId = UUID.randomUUID();
        when(notificationLogRepository.findByTenantId(tenantId)).thenReturn(List.of());
        when(notificationSendPort.isSmsConfigured()).thenReturn(false);
        when(notificationSendPort.isWhatsappConfigured()).thenReturn(true);

        NotificationStatusSummary summary = useCase.execute(tenantId);

        assertThat(summary.smsConfigured()).isFalse();
        assertThat(summary.whatsappConfigured()).isTrue();
    }
}
