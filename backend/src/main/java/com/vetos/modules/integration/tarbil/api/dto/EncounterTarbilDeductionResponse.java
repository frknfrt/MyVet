package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.GetEncounterTarbilDeductionUseCase;

import java.util.List;
import java.util.UUID;

public record EncounterTarbilDeductionResponse(List<Line> lines, UUID submissionId, String submissionStatus) {

    public record Line(String itemName, String lotNumber, int quantity, String status, Integer settledQuantity) {}

    public static EncounterTarbilDeductionResponse from(GetEncounterTarbilDeductionUseCase.View v) {
        return new EncounterTarbilDeductionResponse(
            v.lines().stream().map(l -> new Line(l.itemName(), l.lotNumber(), l.quantity(), l.status().name(), l.settledQuantity())).toList(),
            v.submissionId(), v.submissionStatus() == null ? null : v.submissionStatus().name());
    }
}
