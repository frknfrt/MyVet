package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.domain.DrugRoute;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record IssuePrescriptionRequest(
    @NotNull UUID patientId,
    @NotNull UUID encounterId,
    boolean controlledSubstance,
    @NotEmpty List<Item> items
) {
    public record Item(UUID drugId, String dosage, String frequency, int durationDays, DrugRoute route) {}
}
