package com.vetos.modules.integration.efatura.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Platform admin Sistem Sagligi paneli icin -- basarisiz bir e-Fatura
 * gonderiminin kiraci adiyla birlikte ozeti. bkz. EInvoiceHealthPort.
 */
public record FailedEInvoiceView(
    UUID tenantId, String tenantName, UUID invoiceId, EInvoiceDocumentType documentType,
    BigDecimal totalAmount, String failureReason, int attemptCount, Instant attemptedAt
) {}
