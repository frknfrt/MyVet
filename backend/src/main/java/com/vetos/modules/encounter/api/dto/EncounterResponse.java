package com.vetos.modules.encounter.api.dto;

import com.vetos.modules.encounter.application.dto.EncounterDetail;
import com.vetos.modules.encounter.domain.EncounterStatus;
import com.vetos.modules.encounter.domain.PhysicalExamFinding;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EncounterResponse(
    UUID id,
    UUID patientId,
    String patientName,
    UUID staffUserId,
    String staffName,
    UUID appointmentId,
    Instant encounterDate,
    String subjective,
    String objective,
    String assessment,
    String plan,
    BigDecimal weightKg,
    BigDecimal temperatureC,
    Integer heartRate,
    Integer respiratoryRate,
    String templateUsed,
    List<PhysicalExamFinding> physicalExamFindings,
    EncounterStatus status,
    boolean aiGenerated,
    Instant finalizedAt
) {
    public static EncounterResponse from(EncounterDetail d) {
        return new EncounterResponse(
            d.id(), d.patientId(), d.patientName(), d.staffUserId(), d.staffName(), d.appointmentId(),
            d.encounterDate(), d.subjective(), d.objective(), d.assessment(), d.plan(), d.weightKg(), d.temperatureC(),
            d.heartRate(), d.respiratoryRate(), d.templateUsed(), d.physicalExamFindings(), d.status(), d.aiGenerated(),
            d.finalizedAt()
        );
    }
}
