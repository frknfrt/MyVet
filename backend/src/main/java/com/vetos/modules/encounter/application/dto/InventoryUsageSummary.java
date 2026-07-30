package com.vetos.modules.encounter.application.dto;

import java.util.UUID;

public record InventoryUsageSummary(UUID id, UUID inventoryItemId, int quantity) {}
