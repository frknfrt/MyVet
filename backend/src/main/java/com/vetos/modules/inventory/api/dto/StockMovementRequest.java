package com.vetos.modules.inventory.api.dto;

import com.vetos.modules.inventory.domain.StockMovementType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record StockMovementRequest(@NotNull StockMovementType movementType, @Positive int quantity) {}
