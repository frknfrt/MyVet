package com.vetos.modules.encounter.application.dto;

import java.util.List;
import java.util.UUID;

public record IssuePrescriptionCommand(
    UUID patientId,
    UUID encounterId,
    UUID prescribingStaffId,
    boolean controlledSubstance,
    List<PrescriptionItemInput> items
) {}
