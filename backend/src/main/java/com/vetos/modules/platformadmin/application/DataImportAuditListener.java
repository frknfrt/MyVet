package com.vetos.modules.platformadmin.application;

import com.vetos.modules.dataimport.domain.event.DataImportCommitted;
import com.vetos.modules.dataimport.domain.event.DataImportFailed;
import com.vetos.modules.dataimport.domain.event.DataImportRolledBack;
import com.vetos.modules.platformadmin.domain.AuditAction;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * dataimport modulu platformadmin'e bagimli olamaz (RecordAuditLogUseCase'i dogrudan cagiramaz);
 * yayinladigi olaylar burada platform admin audit kaydina cevrilir. Hedef: aktarimin yapildigi klinik.
 */
@Component
@RequiredArgsConstructor
public class DataImportAuditListener {

    private static final String TARGET_TYPE = "TENANT";

    private final RecordAuditLogUseCase recordAuditLogUseCase;

    @EventListener
    public void on(DataImportCommitted e) {
        record(e.adminId(), e.adminEmail(), AuditAction.DATA_IMPORT_COMMITTED, e.tenantId(), e.details());
    }

    @EventListener
    public void on(DataImportFailed e) {
        record(e.adminId(), e.adminEmail(), AuditAction.DATA_IMPORT_FAILED, e.tenantId(), e.details());
    }

    @EventListener
    public void on(DataImportRolledBack e) {
        record(e.adminId(), e.adminEmail(), AuditAction.DATA_IMPORT_ROLLED_BACK, e.tenantId(), e.details());
    }

    private void record(UUID adminId, String adminEmail, String action, UUID tenantId, String details) {
        // audit_log_entries.platform_admin_email NOT NULL: sistem kaynakli olaylarda e-posta olmayabilir.
        String email = adminEmail == null || adminEmail.isBlank() ? "system" : adminEmail;
        recordAuditLogUseCase.execute(adminId, email, action, TARGET_TYPE, tenantId, details);
    }
}
