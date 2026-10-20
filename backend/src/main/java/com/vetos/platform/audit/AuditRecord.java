package com.vetos.platform.audit;

import java.util.UUID;

/**
 * Yazilacak olay. tenantId null ise TenantContext'ten alinir. summary/targetLabel'da hasta/sahip adi,
 * telefon, TCKN, adres, serbest klinik notu YOK (spec §3 PII kurali).
 */
public record AuditRecord(UUID tenantId, AuditAction action, String targetType, UUID targetId, String targetLabel, String summary) {

    public static AuditRecord of(AuditAction action, String targetType, UUID targetId, String targetLabel, String summary) {
        return new AuditRecord(null, action, targetType, targetId, targetLabel, summary);
    }

    public AuditRecord forTenant(UUID tenantId) {
        return new AuditRecord(tenantId, action, targetType, targetId, targetLabel, summary);
    }
}
