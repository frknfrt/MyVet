package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.InvoiceSummary;
import com.vetos.modules.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceSummaryResponse(UUID id, UUID ownerId, String ownerName, BigDecimal totalAmount, InvoiceStatus status, Instant issuedAt) {
    public static InvoiceSummaryResponse from(InvoiceSummary s) {
        return new InvoiceSummaryResponse(s.id(), s.ownerId(), s.ownerName(), s.totalAmount(), s.status(), s.issuedAt());
    }
}
