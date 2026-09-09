package com.vetos.modules.integration.efatura.api.dto;

import com.vetos.modules.integration.efatura.application.dto.EInvoiceSubmissionSummary;
import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;

import java.time.Instant;
import java.util.UUID;

public record EInvoiceSubmissionResponse(
    UUID id, UUID invoiceId, String ownerName, EInvoiceDocumentType documentType,
    EInvoiceSubmissionStatus status, String gibReference, String failureReason, Instant attemptedAt
) {
    public static EInvoiceSubmissionResponse from(EInvoiceSubmissionSummary s) {
        return new EInvoiceSubmissionResponse(
            s.id(), s.invoiceId(), s.ownerName(), s.documentType(), s.status(), s.gibReference(),
            s.failureReason(), s.attemptedAt()
        );
    }
}
