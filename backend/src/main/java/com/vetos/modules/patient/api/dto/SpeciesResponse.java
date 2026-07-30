package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.application.dto.SpeciesSummary;

import java.util.UUID;

public record SpeciesResponse(UUID id, String name) {
    public static SpeciesResponse from(SpeciesSummary summary) {
        return new SpeciesResponse(summary.id(), summary.name());
    }
}
