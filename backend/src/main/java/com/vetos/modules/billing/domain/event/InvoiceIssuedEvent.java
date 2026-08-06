package com.vetos.modules.billing.domain.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoiceIssuedEvent(
    UUID invoiceId, UUID tenantId, UUID ownerId, BigDecimal totalAmount, BigDecimal taxAmount, Instant issuedAt
) {}
