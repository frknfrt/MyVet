package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.VaccinationStatus;

import java.time.LocalDate;
import java.util.UUID;

public record VaccinationScheduleItem(
    UUID id,
    UUID patientId,
    String patientName,
    UUID ownerId,
    String ownerFullName,
    String vaccineName,
    String lotNumber,
    LocalDate administeredDate,
    LocalDate nextDueDate,
    VaccinationStatus status,
    String notes,
    String administeredByStaffName
) {}
