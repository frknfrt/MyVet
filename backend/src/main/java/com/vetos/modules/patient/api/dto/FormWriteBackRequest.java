package com.vetos.modules.patient.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record FormWriteBackRequest(
    @NotNull UUID ownerId,
    @NotNull UUID patientId,
    UUID signedFormId,
    @NotNull List<Item> items
) {
    public record Item(String binding, String value) {}
}
