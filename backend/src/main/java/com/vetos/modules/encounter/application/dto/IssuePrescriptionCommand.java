package com.vetos.modules.encounter.application.dto;

import java.util.List;
import java.util.UUID;

/** encounterId muayenesiz recetede null; tarbilDiseaseId stoktan verilen kalem varsa zorunlu (spec 2026-10-09 S5.6). */
public record IssuePrescriptionCommand(
    UUID patientId,
    UUID encounterId,
    UUID prescribingStaffId,
    boolean controlledSubstance,
    UUID tarbilDiseaseId,
    List<PrescriptionItemInput> items
) {}
