package com.vetos.modules.encounter.application.dto;

import java.time.LocalDate;
import java.util.UUID;

public record VaccinationRecordSummary(
    UUID id, String vaccineName, String lotNumber, LocalDate administeredDate, LocalDate nextDueDate
) {}
