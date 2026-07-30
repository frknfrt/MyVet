package com.vetos.modules.billing.application.dto;

import com.vetos.modules.billing.domain.InvoiceLineSource;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceDetail(
    UUID id,
    UUID ownerId,
    String ownerName,
    UUID encounterId,
    BigDecimal totalAmount,
    BigDecimal taxAmount,
    BigDecimal paidAmount,
    InvoiceStatus status,
    Instant issuedAt,
    List<Line> lines,
    List<PaymentRecord> payments
) {
    public record Line(UUID id, String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal, InvoiceLineSource source) {}
    public record PaymentRecord(UUID id, PaymentMethod method, BigDecimal amount, Instant paidAt) {}
}
