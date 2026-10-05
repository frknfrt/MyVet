package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.modules.integration.efatura.domain.FailedEInvoiceView;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FailedEInvoiceResponse(
    UUID submissionId, UUID tenantId, String tenantName, UUID invoiceId, EInvoiceDocumentType documentType,
    BigDecimal totalAmount, String failureReason, int attemptCount, Instant attemptedAt
) {
    public static FailedEInvoiceResponse from(FailedEInvoiceView v) {
        return new FailedEInvoiceResponse(
            v.submissionId(), v.tenantId(), v.tenantName(), v.invoiceId(), v.documentType(),
            v.totalAmount(), v.failureReason(), v.attemptCount(), v.attemptedAt()
        );
    }
}
