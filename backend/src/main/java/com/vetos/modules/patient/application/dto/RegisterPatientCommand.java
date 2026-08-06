package com.vetos.modules.patient.application.dto;

import com.vetos.modules.patient.domain.Sex;

import java.time.LocalDate;
import java.util.UUID;

public record RegisterPatientCommand(
    UUID ownerId,
    UUID speciesId,
    UUID breedId,
    String name,
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
