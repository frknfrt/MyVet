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
    LocalDate birthDate
) {}
