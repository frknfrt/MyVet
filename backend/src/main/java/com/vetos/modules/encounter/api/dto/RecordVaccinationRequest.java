package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.domain.VaccinationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record RecordVaccinationRequest(
    @NotNull UUID patientId,
    UUID encounterId,
    @NotBlank String vaccineName,
    String lotNumber,
    @NotNull LocalDate administeredDate,
    LocalDate nextDueDate,
    @NotNull VaccinationStatus status,
    String notes
) {}
