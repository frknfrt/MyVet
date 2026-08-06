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
    String color,
    String temperament,
    String distinguishingMarks,
    boolean aggressive,
    String bloodType,
    String foodBrand,
    String criticalAlert,
    String notes,
    String protocolNumber,
    String rabiesTag,
    PatientStatus status,
    UUID ownerId,
    String ownerFullName,
    String ownerPhone
) {
    public static PatientProfileResponse from(PatientProfile profile) {
        return new PatientProfileResponse(
            profile.id(), profile.name(), profile.speciesName(), profile.breedName(), profile.sex(),
            profile.neutered(), profile.birthDate(), profile.microchipNumber(), profile.tarbilAnimalId(),
            profile.weightKg(), profile.photoUrl(), profile.color(), profile.temperament(),
            profile.distinguishingMarks(), profile.aggressive(), profile.bloodType(), profile.foodBrand(),
            profile.criticalAlert(), profile.notes(), profile.protocolNumber(), profile.rabiesTag(),
            profile.status(), profile.ownerId(), profile.ownerFullName(), profile.ownerPhone()
        );
    }
}
