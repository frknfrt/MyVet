package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.VaccinationStatus;

import java.time.LocalDate;
import java.util.UUID;

public record RecordVaccinationCommand(
    UUID tenantId,
    UUID patientId,
    UUID encounterId,
    String vaccineName,
    String lotNumber,
    LocalDate administeredDate,
    LocalDate nextDueDate,
    UUID administeredByStaffId,
    VaccinationStatus status,
    String notes
) {}
