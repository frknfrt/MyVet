package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.domain.Sex;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;
import java.util.UUID;

public record UpdatePatientRequest(
    @NotBlank String name,
    UUID breedId,
    Sex sex,
    LocalDate birthDate,
    boolean neutered,
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
