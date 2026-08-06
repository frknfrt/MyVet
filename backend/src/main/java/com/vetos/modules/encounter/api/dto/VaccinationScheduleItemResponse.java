package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.application.dto.VaccinationScheduleItem;
import com.vetos.modules.encounter.domain.VaccinationStatus;

import java.time.LocalDate;
import java.util.UUID;

public record VaccinationScheduleItemResponse(
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
) {
    public static VaccinationScheduleItemResponse from(VaccinationScheduleItem i) {
        return new VaccinationScheduleItemResponse(
            i.id(), i.patientId(), i.patientName(), i.ownerId(), i.ownerFullName(),
            i.vaccineName(), i.lotNumber(), i.administeredDate(), i.nextDueDate(),
            i.status(), i.notes(), i.administeredByStaffName()
        );
    }
}
