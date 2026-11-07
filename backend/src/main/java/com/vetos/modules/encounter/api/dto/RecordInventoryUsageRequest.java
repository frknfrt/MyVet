package com.vetos.modules.encounter.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** allergyAcknowledged/allergyOverrideReason: hastanin kritik uyarisi varsa zorunlu (bkz. DrugAllergyGuard). */
public record RecordInventoryUsageRequest(
    @NotNull UUID inventoryItemId,
    @NotNull @com.vetos.platform.validation.StockAmount java.math.BigDecimal quantity,
    Boolean allergyAcknowledged,
    @Size(max = 500) String allergyOverrideReason
) {}
