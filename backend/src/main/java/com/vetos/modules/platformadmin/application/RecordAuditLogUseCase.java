package com.vetos.modules.platformadmin.application;

import com.vetos.modules.platformadmin.domain.AuditLogEntry;
import com.vetos.modules.platformadmin.domain.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Diger use-case'lerin basarili bir hassas islemin sonunda cagirdigi kucuk
 * yardimci -- RecordPlatformPaymentUseCase'in TenantBillingReconciler'i nasil
 * cagirdigina benzer desen. Sadece bir platform admin'in bilerek tetikledigi
 * islemler icin cagrilmali (bkz. AuditLogEntry javadoc).
 */
@Service
@RequiredArgsConstructor
public class RecordAuditLogUseCase {

    private final AuditLogRepository auditLogRepository;

    @Transactional
    public void execute(UUID platformAdminId, String platformAdminEmail, String action, String targetType, UUID targetId, String details) {
        auditLogRepository.save(AuditLogEntry.record(platformAdminId, platformAdminEmail, action, targetType, targetId, details));
    }
}
