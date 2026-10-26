package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.GetEncounterTarbilDeductionUseCase;

import java.util.List;
import java.util.UUID;

public record EncounterTarbilDeductionResponse(List<Line> lines, UUID submissionId, String submissionStatus) {

    public record Line(String itemName, String lotNumber, java.math.BigDecimal quantity, String status, java.math.BigDecimal settledQuantity,
                       /** Kalemin kullanım birimi (ADET | ML | G | FLAKON | TABLET; spec 2026-10-25). */ String unit) {}

    public static EncounterTarbilDeductionResponse from(GetEncounterTarbilDeductionUseCase.View v) {
        return new EncounterTarbilDeductionResponse(
            v.lines().stream().map(l -> new Line(l.itemName(), l.lotNumber(), l.quantity(), l.status().name(), l.settledQuantity(), l.unit())).toList(),
            v.submissionId(), v.submissionStatus() == null ? null : v.submissionStatus().name());
    }
}
