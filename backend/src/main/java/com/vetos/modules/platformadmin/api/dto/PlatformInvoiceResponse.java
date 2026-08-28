package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.PlatformInvoice;
import com.vetos.modules.platformadmin.domain.PlatformInvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record PlatformInvoiceResponse(
    UUID id, String planCode, BigDecimal amount, LocalDate periodStart, LocalDate periodEnd,
    LocalDate dueDate, PlatformInvoiceStatus status, Instant issuedAt, Instant paidAt
) {
    public static PlatformInvoiceResponse from(PlatformInvoice invoice) {
        return new PlatformInvoiceResponse(
            invoice.getId(), invoice.getPlanCode(), invoice.getAmount(), invoice.getPeriodStart(), invoice.getPeriodEnd(),
            invoice.getDueDate(), invoice.getStatus(), invoice.getIssuedAt(), invoice.getPaidAt()
        );
    }
}
