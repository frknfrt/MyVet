package com.vetos.modules.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInventoryItemCommand(
    UUID branchId,
    String name,
    String category,
    String skuBarcode,
    int initialQuantity,
    int reorderThreshold,
    LocalDate expiryDate,
    String lotNumber,
    BigDecimal unitCost
) {}
