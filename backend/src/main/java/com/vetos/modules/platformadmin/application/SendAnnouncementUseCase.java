package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.Announcement;
import com.vetos.modules.platformadmin.domain.AnnouncementRepository;
import com.vetos.modules.platformadmin.domain.AuditAction;
import com.vetos.modules.platformadmin.domain.PlatformAnnouncementEmailPort;
import com.vetos.modules.tenant.domain.TenantAdminOverview;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.modules.tenant.domain.TenantStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Platform admin'in TUM (askiya alinmamis) kiracilarin faturalama iletisim
 * adresine tek seferde duyuru gondermesi icin -- bakim/yeni ozellik/fiyat
 * degisikligi gibi. SUSPENDED kiracilar atlanir (zaten aktif kullanmiyorlar);
 * faturalama iletisim e-postasi olmayan kiracilar da atlanir (recipientCount'a
 * dahil edilmez).
 */
@Service
@RequiredArgsConstructor
public class SendAnnouncementUseCase {

    private final TenantAdminPort tenantAdminPort;
    private final PlatformAnnouncementEmailPort platformAnnouncementEmailPort;
    private final AnnouncementRepository announcementRepository;
    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @Transactional
    public UUID execute(String title, String body, UUID platformAdminId, String platformAdminEmail) {
        int recipientCount = 0;
        for (TenantAdminOverview tenant : tenantAdminPort.listAll()) {
            if (tenant.status() == TenantStatus.SUSPENDED) continue;
            String recipientEmail = tenantAdminPort.findBillingContactEmail(tenant.tenantId()).orElse(null);
            if (recipientEmail == null) continue;
            platformAnnouncementEmailPort.sendAnnouncement(tenant.name(), recipientEmail, title, body);
            recipientCount++;
        }

        Announcement announcement = announcementRepository.save(
            Announcement.record(title, body, platformAdminId, platformAdminEmail, recipientCount)
        );
        recordAuditLogUseCase.execute(
            platformAdminId, platformAdminEmail, AuditAction.ANNOUNCEMENT_SENT, "ANNOUNCEMENT", announcement.getId(),
            title + " (" + recipientCount + " alici)"
        );
        return announcement.getId();
    }
}
