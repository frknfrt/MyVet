package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.VaccinationStatus;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Periyodik asi serisi olusturma komutu -- orn. "60 gunde 10 doz".
 * firstDoseStatus ilk dozun durumunu belirler (bugun yapildiysa ADMINISTERED,
 * tamami ileri tarihliyse SCHEDULED); 2. dozdan itibaren tum dozlar daima
 * SCHEDULED olarak olusturulur.
 */
public record RecordVaccinationSeriesCommand(
    UUID tenantId,
    UUID patientId,
    UUID encounterId,
    String vaccineName,
    String lotNumber,
    LocalDate startDate,
    int intervalDays,
    int doseCount,
    VaccinationStatus firstDoseStatus,
    UUID administeredByStaffId,
    String notes
) {}
