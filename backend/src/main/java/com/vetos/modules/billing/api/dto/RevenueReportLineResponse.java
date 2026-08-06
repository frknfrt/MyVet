package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.RevenueReportLine;
import com.vetos.modules.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RevenueReportLineResponse(
    UUID invoiceId, String ownerName, String branchName, Instant issuedAt,
    InvoiceStatus status, BigDecimal totalAmount, BigDecimal paidAmount
) {
    public static RevenueReportLineResponse from(RevenueReportLine l) {
        return new RevenueReportLineResponse(
            l.invoiceId(), l.ownerName(), l.branchName(), l.issuedAt(), l.status(), l.totalAmount(), l.paidAmount()
        );
    }
}
