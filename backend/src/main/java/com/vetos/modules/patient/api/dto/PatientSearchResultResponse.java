package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.application.dto.PatientSearchResult;
import com.vetos.modules.patient.domain.PatientStatus;

import java.util.UUID;

public record PatientSearchResultResponse(
    UUID patientId,
    String patientName,
    String speciesName,
    String breedName,
    PatientStatus status,
    UUID ownerId,
    String ownerFullName,
    String ownerPhone
) {
    public static PatientSearchResultResponse from(PatientSearchResult result) {
        return new PatientSearchResultResponse(
            result.patientId(), result.patientName(), result.speciesName(), result.breedName(), result.status(),
            result.ownerId(), result.ownerFullName(), result.ownerPhone()
        );
    }
}
