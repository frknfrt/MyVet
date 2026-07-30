package com.vetos.modules.patient.application.dto;

import com.vetos.modules.patient.domain.Sex;

import java.time.LocalDate;
import java.util.UUID;

public record UpdatePatientCommand(
    UUID patientId,
    String name,
    UUID breedId,
    Sex sex,
    LocalDate birthDate,
    boolean neutered
) {}
