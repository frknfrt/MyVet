package com.vetos.modules.integration.efatura.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Platform admin Sistem Sagligi paneli icin -- basarisiz bir e-Fatura
 * gonderiminin kiraci adiyla birlikte ozeti. bkz. EInvoiceHealthPort.
 * submissionId, manuel "Tekrar Dene" aksiyonu icin gerekli (bkz.
 * EInvoiceAdminPort) -- invoiceId ile karistirilmamali, o ilgili faturanin
 * (billing modulu) kimligidir.
 */
public record FailedEInvoiceView(
    UUID submissionId, UUID tenantId, String tenantName, UUID invoiceId, EInvoiceDocumentType documentType,
    BigDecimal totalAmount, String failureReason, int attemptCount, Instant attemptedAt
) {}
