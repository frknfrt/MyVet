package com.vetos.modules.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateInventoryItemRequest(@NotBlank String name, String category, @PositiveOrZero int reorderThreshold, BigDecimal unitCost) {}
