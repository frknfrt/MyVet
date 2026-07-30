package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.application.dto.InventoryUsageSummary;

import java.util.UUID;

public record InventoryUsageResponse(UUID id, UUID inventoryItemId, int quantity) {
    public static InventoryUsageResponse from(InventoryUsageSummary s) {
        return new InventoryUsageResponse(s.id(), s.inventoryItemId(), s.quantity());
    }
}
