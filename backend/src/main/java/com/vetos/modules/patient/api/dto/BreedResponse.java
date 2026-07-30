package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.application.dto.BreedSummary;

import java.util.UUID;

public record BreedResponse(UUID id, String name) {
    public static BreedResponse from(BreedSummary summary) {
        return new BreedResponse(summary.id(), summary.name());
    }
}
