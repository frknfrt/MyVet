package com.vetos.modules.patient.application.dto;

import com.vetos.modules.patient.domain.PatientStatus;
import com.vetos.modules.patient.domain.Sex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PatientProfile(
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
) {}
