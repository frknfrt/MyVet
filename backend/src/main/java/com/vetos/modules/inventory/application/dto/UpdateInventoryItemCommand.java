package com.vetos.modules.inventory.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateInventoryItemCommand(UUID itemId, String name, String category, int reorderThreshold, BigDecimal unitCost) {}
