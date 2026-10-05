package com.vetos.modules.platformadmin.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Platform admin panelinden yapilan hassas islemlerin degismez kaydi
 * (bkz. docs/architecture.md). Impersonate, kiraci askiya alma/aktif etme,
 * plan/kupon degisiklikleri, manuel odeme/fatura iptali gibi islemler
 * ilgili use-case'in sonunda RecordAuditLogUseCase araciligiyla buraya
 * yaziliyor -- otomatik/sistem islemleri (ornegin FlagOverdueAndSuspendUseCase'in
 * gecikme yuzunden askiya almasi) BURAYA YAZILMAZ, sadece bir platform admin'in
 * bilerek tetikledigi islemler.
 */
@Entity
@Table(name = "audit_log_entries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "platform_admin_id", nullable = false)
    private UUID platformAdminId;

    @Column(name = "platform_admin_email", nullable = false)
    private String platformAdminEmail;

    @Column(nullable = false)
    private String action;

    @Column(name = "target_type", nullable = false)
    private String targetType;

    @Column(name = "target_id")
    private UUID targetId;

    @Column
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static AuditLogEntry record(
        UUID platformAdminId, String platformAdminEmail, String action, String targetType, UUID targetId, String details
    ) {
        AuditLogEntry entry = new AuditLogEntry();
        entry.platformAdminId = platformAdminId;
        entry.platformAdminEmail = platformAdminEmail;
        entry.action = action;
        entry.targetType = targetType;
        entry.targetId = targetId;
        entry.details = details;
        entry.createdAt = Instant.now();
        return entry;
    }
}
