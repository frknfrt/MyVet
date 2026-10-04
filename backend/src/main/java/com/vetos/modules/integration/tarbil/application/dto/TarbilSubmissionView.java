package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilDocumentType;
import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Eklentiye giden aktarim verisi -- sahip TC/adres/telefon BILINCLI olarak yok (spec S4). */
public record TarbilSubmissionView(
    UUID id, UUID vaccinationRecordId, TarbilSyncStatus status,
    String patientName, String microchipNumber, UUID speciesId, String speciesName, String breedName,
    String sex, LocalDate birthDate,
    String vaccineName, String lotNumber, LocalDate administeredDate,
    Instant submittedAt, TarbilConfirmationMethod confirmationMethod, String tarbilReference,
    String vaccineKey, String vaccineMappingJson, String speciesMappingJson,
    TarbilDocumentType documentType
) {}
