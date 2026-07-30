package com.vetos.modules.billing.application.dto;

import com.vetos.modules.billing.domain.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceSummary(
    UUID id, UUID ownerId, String ownerName, BigDecimal totalAmount, InvoiceStatus status, Instant issuedAt
) {}
