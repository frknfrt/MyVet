package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.InvoiceDetail;
import com.vetos.modules.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    UUID ownerId,
    String ownerName,
    UUID encounterId,
    BigDecimal totalAmount,
    BigDecimal taxAmount,
    BigDecimal paidAmount,
    InvoiceStatus status,
    Instant issuedAt,
    List<InvoiceDetail.Line> lines,
    List<InvoiceDetail.PaymentRecord> payments
) {
    public static InvoiceResponse from(InvoiceDetail d) {
        return new InvoiceResponse(
            d.id(), d.ownerId(), d.ownerName(), d.encounterId(), d.totalAmount(), d.taxAmount(),
            d.paidAmount(), d.status(), d.issuedAt(), d.lines(), d.payments()
        );
    }
}
