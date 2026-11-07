package com.vetos.modules.encounter.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CheckAllergiesRequest(
    @NotNull UUID patientId,
    @Size(max = 50) List<UUID> drugIds,
    @Size(max = 50) List<UUID> inventoryItemIds
) {}
