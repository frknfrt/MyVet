package com.vetos.modules.integration.efatura.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "efatura_submission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EInvoiceSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false)
    private EInvoiceDocumentType documentType;

    @Column(name = "total_amount", nullable = false)
    private BigDecimal totalAmount;

    @Column(name = "tax_amount", nullable = false)
    private BigDecimal taxAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EInvoiceSubmissionStatus status;

    @Column(name = "gib_reference")
    private String gibReference;

    // Saglayicinin (faturaentegrator) kendi takip ID'si -- PROCESSING durumunda
    // set edilir, callback bu degerle submission'i bulur (bkz. V32 migration).
    @Column(name = "provider_reference")
    private String providerReference;

    // Basarisizlik sebebi (saglayici hata mesaji ya da bizim on-dogrulama
    // mesajimiz) -- personel neden basarisiz oldugunu MyVet arayuzunde
    // gorsun diye ayrica saklanir (bkz. V33 migration).
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "attempted_at", nullable = false)
    private Instant attemptedAt;

    public static EInvoiceSubmission queue(
        UUID tenantId, UUID invoiceId, UUID ownerId, EInvoiceDocumentType documentType, BigDecimal totalAmount, BigDecimal taxAmount
    ) {
        EInvoiceSubmission submission = new EInvoiceSubmission();
        submission.tenantId = tenantId;
        submission.invoiceId = invoiceId;
        submission.ownerId = ownerId;
        submission.documentType = documentType;
        submission.totalAmount = totalAmount;
        submission.taxAmount = taxAmount;
        submission.status = EInvoiceSubmissionStatus.PENDING;
        submission.attemptedAt = Instant.now();
        return submission;
    }

    public void markSubmitted(String gibReference) {
        this.status = EInvoiceSubmissionStatus.SUBMITTED;
        this.gibReference = gibReference;
        this.failureReason = null;
        this.attemptedAt = Instant.now();
    }

    /** Asenkron saglayici istegi kabul etti ama GIB resmilesmesi henuz tamamlanmadi. */
    public void markAcceptedByProvider(String providerReference) {
        this.status = EInvoiceSubmissionStatus.PROCESSING;
        this.providerReference = providerReference;
        this.failureReason = null;
        this.attemptedAt = Instant.now();
        this.nextRetryAt = null;
    }

    public void markFailed(String reason, Instant nextRetryAt) {
        this.status = EInvoiceSubmissionStatus.FAILED;
        this.failureReason = reason;
        this.attemptedAt = Instant.now();
        this.attemptCount++;
        this.nextRetryAt = nextRetryAt;
    }

    public void markRetrying() {
        this.status = EInvoiceSubmissionStatus.PENDING;
        this.failureReason = null;
        this.attemptedAt = Instant.now();
        this.nextRetryAt = null;
    }
}
