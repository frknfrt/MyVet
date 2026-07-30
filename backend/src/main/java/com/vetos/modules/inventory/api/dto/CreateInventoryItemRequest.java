package com.vetos.modules.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInventoryItemRequest(
    @NotBlank String name,
    String category,
    String skuBarcode,
    @PositiveOrZero int initialQuantity,
    @PositiveOrZero int reorderThreshold,
    LocalDate expiryDate,
    String lotNumber,
    BigDecimal unitCost
) {}
