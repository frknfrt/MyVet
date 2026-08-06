package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.application.dto.OwnerSearchResult;

import java.util.UUID;

public record OwnerSearchResultResponse(UUID id, String fullName, String phone) {
    public static OwnerSearchResultResponse from(OwnerSearchResult r) {
        return new OwnerSearchResultResponse(r.id(), r.fullName(), r.phone());
    }
}
