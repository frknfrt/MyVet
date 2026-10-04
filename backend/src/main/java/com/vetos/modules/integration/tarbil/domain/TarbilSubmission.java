package com.vetos.modules.integration.tarbil.domain;

import com.vetos.modules.integration.tarbil.domain.exception.TarbilSubmissionStateConflictException;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Bir Vetly kaydinin (asi, recete, stok kabul) TARBIL'e aktarim durumu. Sunucu TARBIL'e hicbir sey
 * gondermez; resmi kaydi hekim TARBIL arayuzunde onaylar, eklenti (ya da hekim elle) bunu buraya bildirir.
 * source_id: VACCINATION -> vaccination_records.id, PRESCRIPTION -> prescriptions.id,
 * STOCK_RECEIPT -> stock_receipts.id. Bkz. docs/superpowers/specs/2026-10-04-tarbil-otomasyon-cekirdegi-design.md.
 */
@Entity
@Table(name = "tarbil_submission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /** Stok kabulde hasta yoktur. */
    @Column(name = "patient_id")
    private UUID patientId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private TarbilDocumentType documentType;

    @Column(name = "source_id", nullable = false)
    private UUID sourceId;

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

    public static TarbilSubmission queue(UUID tenantId, TarbilDocumentType type, UUID patientId, UUID sourceId) {
        TarbilSubmission submission = new TarbilSubmission();
        submission.tenantId = tenantId;
        submission.documentType = type;
        submission.patientId = patientId;
        submission.sourceId = sourceId;
        submission.status = TarbilSyncStatus.PENDING;
        submission.queuedAt = Instant.now();
        return submission;
    }

    public static TarbilSubmission queueVaccination(UUID tenantId, UUID patientId, UUID vaccinationRecordId) {
        return queue(tenantId, TarbilDocumentType.VACCINATION, patientId, vaccinationRecordId);
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
