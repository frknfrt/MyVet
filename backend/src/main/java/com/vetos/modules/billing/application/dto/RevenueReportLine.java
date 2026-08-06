package com.vetos.modules.billing.application.dto;

import com.vetos.modules.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RevenueReportLine(
    UUID invoiceId, String ownerName, String branchName, Instant issuedAt,
    InvoiceStatus status, BigDecimal totalAmount, BigDecimal paidAmount
) {}
