package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.TarbilDiseaseSummary;

import java.util.UUID;

public record TarbilDiseaseResponse(UUID id, UUID parentId, String name, String path, boolean selectable) {
    public static TarbilDiseaseResponse from(TarbilDiseaseSummary s) {
        return new TarbilDiseaseResponse(s.id(), s.parentId(), s.name(), s.path(), s.selectable());
    }
}
