package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Announcement;
import com.vetos.modules.platformadmin.domain.AnnouncementRepository;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.PlatformAnnouncementEmailPort;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SendAnnouncementUseCaseTest {

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlatformAnnouncementEmailPort platformAnnouncementEmailPort;
    @Mock private AnnouncementRepository announcementRepository;
    @Mock private RecordAuditLogUseCase recordAuditLogUseCase;

    private SendAnnouncementUseCase useCase() {
        return new SendAnnouncementUseCase(tenantAdminPort, platformAnnouncementEmailPort, announcementRepository, recordAuditLogUseCase);
    }

    private TenantAdminOverview tenant(UUID id, String name, TenantStatus status) {
        return new TenantAdminOverview(
            id, name, "1111111111", status, Instant.now(), "PRO", BillingStatus.ACTIVE,
            LocalDate.now(), LocalDate.now().plusMonths(1), 1, 1, null, null
        );
    }

    @Test
    void should_sendToActiveTenantsWithBillingEmail_andSkipSuspendedAndEmailless() {
        UUID activeWithEmail = UUID.randomUUID();
        UUID activeWithoutEmail = UUID.randomUUID();
        UUID suspended = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();

        when(tenantAdminPort.listAll()).thenReturn(List.of(
            tenant(activeWithEmail, "Pati Vet", TenantStatus.ACTIVE),
            tenant(activeWithoutEmail, "Eposta Yok Klinik", TenantStatus.ACTIVE),
            tenant(suspended, "Askidaki Klinik", TenantStatus.SUSPENDED)
        ));
        when(tenantAdminPort.findBillingContactEmail(activeWithEmail)).thenReturn(Optional.of("admin@pati.com"));
        when(tenantAdminPort.findBillingContactEmail(activeWithoutEmail)).thenReturn(Optional.empty());
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UUID announcementId = useCase().execute("Bakim Bildirimi", "Bu gece bakim var.", adminId, "admin@vetly.com.tr");

        verify(platformAnnouncementEmailPort).sendAnnouncement("Pati Vet", "admin@pati.com", "Bakim Bildirimi", "Bu gece bakim var.");
        verify(platformAnnouncementEmailPort, never()).sendAnnouncement(eq("Askidaki Klinik"), any(), any(), any());
        verify(tenantAdminPort, never()).findBillingContactEmail(suspended);

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientCount()).isEqualTo(1);

        verify(recordAuditLogUseCase).execute(
            eq(adminId), eq("admin@vetly.com.tr"), eq(AuditAction.ANNOUNCEMENT_SENT), eq("ANNOUNCEMENT"), eq(announcementId), eq("Bakim Bildirimi (1 alici)")
        );
    }

    @Test
    void should_recordZeroRecipients_when_noTenantsHaveBillingEmail() {
        when(tenantAdminPort.listAll()).thenReturn(List.of());
        when(announcementRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        useCase().execute("Baslik", "Govde", UUID.randomUUID(), "admin@vetly.com.tr");

        ArgumentCaptor<Announcement> captor = ArgumentCaptor.forClass(Announcement.class);
        verify(announcementRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientCount()).isZero();
        verifyNoInteractions(platformAnnouncementEmailPort);
    }
}
