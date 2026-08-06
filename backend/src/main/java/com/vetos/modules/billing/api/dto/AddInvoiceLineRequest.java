package com.vetos.modules.billing.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

public record AddInvoiceLineRequest(
    @NotBlank String description,
    @Positive int quantity,
    @NotNull BigDecimal unitPrice,
    @PositiveOrZero BigDecimal discountAmount,
    @PositiveOrZero BigDecimal vatRate,
    UUID serviceTypeId
) {}
