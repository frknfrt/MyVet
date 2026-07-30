package com.vetos.modules.inventory.api.dto;

import com.vetos.modules.inventory.application.dto.InventoryItemDetail;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InventoryItemResponse(
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
) {
    public static InventoryItemResponse from(InventoryItemDetail d) {
        return new InventoryItemResponse(
            d.id(), d.name(), d.category(), d.skuBarcode(), d.quantityOnHand(), d.reorderThreshold(),
            d.belowReorderThreshold(), d.expiryDate(), d.lotNumber(), d.unitCost()
        );
    }
}
