package com.vetos.modules.encounter.application.dto;

import com.vetos.modules.encounter.domain.PrescriptionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PrescriptionDetail(
    UUID id,
    UUID patientId,
    UUID encounterId,
    LocalDate issuedDate,
    PrescriptionStatus status,
    boolean controlledSubstance,
    List<PrescriptionItemDetail> items
) {
    public record PrescriptionItemDetail(UUID drugId, String drugName, String dosage, String frequency, int durationDays, String route) {}
}
