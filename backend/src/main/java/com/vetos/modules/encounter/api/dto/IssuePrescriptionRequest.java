package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.domain.DrugRoute;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;
import java.util.UUID;

/** encounterId muayenesiz recetede bos; inventoryItemId + stockQuantity "klinigin stogundan ver" (spec 2026-10-09 S5.6). */
public record IssuePrescriptionRequest(
    @NotNull UUID patientId,
    UUID encounterId,
    boolean controlledSubstance,
    UUID tarbilDiseaseId,
    @NotEmpty @Valid List<Item> items
) {
    public record Item(UUID drugId, String drugName, String dosage, String frequency, int durationDays, DrugRoute route,
                       UUID inventoryItemId, @Positive Integer stockQuantity) {}
}
