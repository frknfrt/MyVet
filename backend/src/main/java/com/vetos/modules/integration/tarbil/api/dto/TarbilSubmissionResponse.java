package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.vetos.modules.integration.tarbil.application.dto.TarbilSubmissionView;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TarbilSubmissionResponse(
    UUID id, UUID vaccinationRecordId, TarbilSyncStatus status,
    String patientName, String microchipNumber, String passportNumber, UUID speciesId, String speciesName, String breedName,
    String sex, LocalDate birthDate, String vaccineName, String lotNumber, String tarbilProductName, LocalDate administeredDate,
    Instant submittedAt, TarbilConfirmationMethod confirmationMethod, String tarbilReference,
    String vaccineKey, @JsonRawValue String vaccineMapping, @JsonRawValue String speciesMapping,
    TarbilDocumentType documentType
) {
    public static TarbilSubmissionResponse from(TarbilSubmissionView v) {
        return new TarbilSubmissionResponse(v.id(), v.vaccinationRecordId(), v.status(), v.patientName(), v.microchipNumber(), v.passportNumber(),
            v.speciesId(), v.speciesName(), v.breedName(), v.sex(), v.birthDate(), v.vaccineName(), v.lotNumber(), v.tarbilProductName(),
            v.administeredDate(), v.submittedAt(), v.confirmationMethod(), v.tarbilReference(),
            v.vaccineKey(), v.vaccineMappingJson(), v.speciesMappingJson(), v.documentType());
    }
}
