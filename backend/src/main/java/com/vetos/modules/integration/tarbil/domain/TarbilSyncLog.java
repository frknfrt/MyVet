package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionStateConflictException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Bir asinin TARBIL'e aktarim durumu. Sunucu TARBIL'e hicbir sey gondermez;
 * gonderimi hekim TARBIL arayuzunde yapar, eklenti (ya da hekim elle) bunu
 * buraya bildirir. Bkz. docs/superpowers/specs/2026-10-02-tarbil-eklenti-design.md.
 */
@Entity
@Table(name = "tarbil_sync_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "vaccination_record_id", nullable = false, unique = true)
    private UUID vaccinationRecordId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sync_type", nullable = false)
    private TarbilSyncType syncType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TarbilSyncStatus status;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "submitted_by_staff_id")
    private UUID submittedByStaffId;

    @Enumerated(EnumType.STRING)
    @Column(name = "confirmation_method")
    private TarbilConfirmationMethod confirmationMethod;

    @Column(name = "tarbil_reference")
    private String tarbilReference;

    @Column(name = "dismissed_reason")
    private String dismissedReason;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @Column(name = "dismissed_by_staff_id")
    private UUID dismissedByStaffId;

    public static TarbilSyncLog queueVaccination(UUID tenantId, UUID patientId, UUID vaccinationRecordId) {
        TarbilSyncLog log = new TarbilSyncLog();
        log.tenantId = tenantId;
        log.patientId = patientId;
        log.vaccinationRecordId = vaccinationRecordId;
        log.syncType = TarbilSyncType.VACCINATION;
        log.status = TarbilSyncStatus.PENDING;
        log.queuedAt = Instant.now();
        return log;
    }

    /** @return true ise durum degisti; false ise zaten SUBMITTED'di (idempotent, ilk kayit korunur). */
    public boolean markSubmitted(UUID staffId, TarbilConfirmationMethod method, String reference, Instant now) {
        if (status == TarbilSyncStatus.SUBMITTED) {
            return false;
        }
        // DISMISSED'tan da gecilir: TARBIL'e gercekten kaydedildiyse "bildirilmeyecek" niyeti olgunun onune gecemez.
        this.dismissedReason = null;
        this.dismissedByStaffId = null;
        this.dismissedAt = null;
        this.status = TarbilSyncStatus.SUBMITTED;
        this.submittedByStaffId = staffId;
        this.confirmationMethod = method;
        this.tarbilReference = reference;
        this.submittedAt = now;
        return true;
    }

    public void dismiss(UUID staffId, String reason, Instant now) {
        if (status != TarbilSyncStatus.PENDING) {
            throw new TarbilSubmissionStateConflictException(status, "bildirilmeyecek isaretlemesi");
        }
        this.status = TarbilSyncStatus.DISMISSED;
        this.dismissedReason = reason;
        this.dismissedByStaffId = staffId;
        this.dismissedAt = now;
    }

    public void restore() {
        if (status != TarbilSyncStatus.DISMISSED) {
            throw new TarbilSubmissionStateConflictException(status, "geri alma");
        }
        this.status = TarbilSyncStatus.PENDING;
        this.dismissedReason = null;
        this.dismissedByStaffId = null;
        this.dismissedAt = null;
    }
}
