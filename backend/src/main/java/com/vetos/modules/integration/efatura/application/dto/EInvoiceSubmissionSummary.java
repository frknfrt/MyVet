package com.vetos.modules.integration.efatura.application.dto;

import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;

import java.time.Instant;
import java.util.UUID;

public record EInvoiceSubmissionSummary(
    UUID id, UUID invoiceId, String ownerName, EInvoiceDocumentType documentType,
    EInvoiceSubmissionStatus status, String gibReference, String failureReason, Instant attemptedAt
) {}
