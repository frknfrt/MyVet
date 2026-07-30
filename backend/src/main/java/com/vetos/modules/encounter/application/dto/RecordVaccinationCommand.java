package com.vetos.modules.encounter.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record RecordVaccinationCommand(
    UUID patientId,
    UUID encounterId,
    String vaccineName,
    String lotNumber,
    LocalDate administeredDate,
    LocalDate nextDueDate,
    UUID administeredByStaffId
) {}
