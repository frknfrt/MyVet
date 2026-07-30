package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.application.dto.PrescriptionDetail;
import com.vetos.modules.encounter.domain.PrescriptionStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PrescriptionResponse(
    UUID id,
    UUID patientId,
    UUID encounterId,
    LocalDate issuedDate,
    PrescriptionStatus status,
    boolean controlledSubstance,
    List<PrescriptionDetail.PrescriptionItemDetail> items
) {
    public static PrescriptionResponse from(PrescriptionDetail d) {
        return new PrescriptionResponse(d.id(), d.patientId(), d.encounterId(), d.issuedDate(), d.status(), d.controlledSubstance(), d.items());
    }
}
