package com.vetos.modules.inventory.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryItemDetail(
    UUID id,
    String name,
    String category,
    String skuBarcode,
    int quantityOnHand,
    int reorderThreshold,
    boolean belowReorderThreshold,
    LocalDate expiryDate,
    String lotNumber,
    BigDecimal unitCost
) {}
