package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.domain.Sex;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record RegisterPatientRequest(
    @NotNull UUID ownerId,
    @NotNull UUID speciesId,
    UUID breedId,
    @NotBlank String name,
    Sex sex,
    LocalDate birthDate,
    String color,
    String temperament,
    String distinguishingMarks,
    boolean aggressive,
    String bloodType,
    String foodBrand,
    String criticalAlert,
    String notes,
    String protocolNumber,
    String rabiesTag
) {}
