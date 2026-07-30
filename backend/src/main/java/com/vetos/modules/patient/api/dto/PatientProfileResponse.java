package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.application.dto.PatientProfile;
import com.vetos.modules.patient.domain.PatientStatus;
import com.vetos.modules.patient.domain.Sex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PatientProfileResponse(
    UUID id,
    String name,
    String speciesName,
    String breedName,
    Sex sex,
    boolean neutered,
    LocalDate birthDate,
    String microchipNumber,
    String tarbilAnimalId,
    BigDecimal weightKg,
    String photoUrl,
    PatientStatus status,
    UUID ownerId,
    String ownerFullName,
    String ownerPhone
) {
    public static PatientProfileResponse from(PatientProfile profile) {
        return new PatientProfileResponse(
            profile.id(), profile.name(), profile.speciesName(), profile.breedName(), profile.sex(),
            profile.neutered(), profile.birthDate(), profile.microchipNumber(), profile.tarbilAnimalId(),
            profile.weightKg(), profile.photoUrl(), profile.status(),
            profile.ownerId(), profile.ownerFullName(), profile.ownerPhone()
        );
    }
}
