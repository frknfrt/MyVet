package com.vetos.modules.integration.tarbil.api.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.vetos.modules.integration.tarbil.application.dto.TarbilMappingSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;

import java.time.Instant;
import java.util.UUID;

public record TarbilMappingResponse(UUID id, TarbilMappingKind kind, String vetlyKey,
                                    @JsonRawValue String tarbilFields, Instant updatedAt) {
    public static TarbilMappingResponse from(TarbilMappingSummary s) {
        return new TarbilMappingResponse(s.id(), s.kind(), s.vetlyKey(), s.tarbilFields(), s.updatedAt());
    }
}
