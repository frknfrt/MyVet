package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.domain.VaccinationStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record RecordVaccinationSeriesRequest(
    @NotNull UUID patientId,
    UUID encounterId,
    @NotBlank String vaccineName,
    String lotNumber,
    @NotNull LocalDate startDate,
    @Min(1) @Max(3650) int intervalDays,
    @Min(2) @Max(30) int doseCount,
    @NotNull VaccinationStatus firstDoseStatus,
    String notes
) {}
