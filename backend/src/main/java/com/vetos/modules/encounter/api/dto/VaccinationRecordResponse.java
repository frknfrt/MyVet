package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.application.dto.VaccinationRecordSummary;

import java.time.LocalDate;
import java.util.UUID;

public record VaccinationRecordResponse(UUID id, String vaccineName, String lotNumber, LocalDate administeredDate, LocalDate nextDueDate) {
    public static VaccinationRecordResponse from(VaccinationRecordSummary s) {
        return new VaccinationRecordResponse(s.id(), s.vaccineName(), s.lotNumber(), s.administeredDate(), s.nextDueDate());
    }
}
