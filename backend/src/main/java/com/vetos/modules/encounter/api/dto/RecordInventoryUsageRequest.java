package com.vetos.modules.encounter.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record RecordInventoryUsageRequest(@NotNull UUID inventoryItemId, @NotNull @Positive java.math.BigDecimal quantity) {}
