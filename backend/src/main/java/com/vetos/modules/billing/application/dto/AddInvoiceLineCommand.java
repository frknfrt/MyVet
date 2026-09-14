package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record AddInvoiceLineCommand(
    UUID invoiceId, String description, int quantity, BigDecimal unitPrice,
    BigDecimal discountAmount, BigDecimal vatRate, UUID serviceTypeId, UUID inventoryItemId
) {}
