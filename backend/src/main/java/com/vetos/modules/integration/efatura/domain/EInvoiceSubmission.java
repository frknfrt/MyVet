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
        this.attemptedAt = Instant.now();
    }

    public void markFailed() {
        this.status = EInvoiceSubmissionStatus.FAILED;
        this.attemptedAt = Instant.now();
    }

    public void markRetrying() {
        this.status = EInvoiceSubmissionStatus.PENDING;
        this.attemptedAt = Instant.now();
    }
}
