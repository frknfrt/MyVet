package com.vetos.modules.platformadmin.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceAdminPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceHealthPort;
import com.vetos.modules.integration.tarbil.domain.FailedTarbilSyncView;
import com.vetos.modules.integration.tarbil.domain.TarbilAdminPort;
import com.vetos.modules.integration.tarbil.domain.TarbilHealthPort;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;
import com.vetos.modules.notification.domain.NotificationAdminPort;
import com.vetos.modules.notification.domain.NotificationHealthPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListPlatformSystemHealthUseCaseTest {

    @Mock private NotificationHealthPort notificationHealthPort;
    @Mock private NotificationAdminPort notificationAdminPort;
    @Mock private EInvoiceHealthPort eInvoiceHealthPort;
    @Mock private EInvoiceAdminPort eInvoiceAdminPort;
    @Mock private TarbilHealthPort tarbilHealthPort;
    @Mock private TarbilAdminPort tarbilAdminPort;

    private ListPlatformSystemHealthUseCase useCase() {
        return new ListPlatformSystemHealthUseCase(
            notificationHealthPort, notificationAdminPort, eInvoiceHealthPort, eInvoiceAdminPort, tarbilHealthPort, tarbilAdminPort
        );
    }

    @Test
    void should_delegateToTarbilHealthPort_when_failedTarbilSyncs() {
        FailedTarbilSyncView view = new FailedTarbilSyncView(
            UUID.randomUUID(), UUID.randomUUID(), "Pati Veteriner", UUID.randomUUID(), "Tekir",
            TarbilSyncType.VACCINATION, "hata", 1, Instant.now()
        );
        when(tarbilHealthPort.findRecentFailed(100)).thenReturn(List.of(view));

        List<FailedTarbilSyncView> result = useCase().failedTarbilSyncs();

        assertThat(result).containsExactly(view);
    }

    @Test
    void should_delegateToTarbilAdminPort_when_retryTarbilSync() {
        UUID logId = UUID.randomUUID();

        useCase().retryTarbilSync(logId);

        verify(tarbilAdminPort).retryNow(logId);
    }
}
