package com.vetos.modules.integration.efatura.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record EInvoiceSubmissionRequest(
    UUID invoiceId, EInvoiceDocumentType documentType, String buyerName, String buyerAddress,
    String buyerIdentifier, BigDecimal totalAmount, BigDecimal taxAmount
) {}
