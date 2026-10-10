package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.inventory.domain.ReportedDeductionLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** lines: eklentinin TARBIL'de onaylanan urun satirlari (spec 2026-10-09 S5.3); MANUAL'da yok sayilir. */
public record MarkSubmittedRequest(@NotNull TarbilConfirmationMethod method, @Size(max = 100) String tarbilReference,
                                   @Valid @Size(max = 50) List<Line> lines) {

    public record Line(@NotBlank @Size(max = 300) String productName, @Size(max = 100) String serialNumber, @Positive int quantity) {}

    public List<ReportedDeductionLine> toReported() {
        return lines == null ? null : lines.stream().map(l -> new ReportedDeductionLine(l.productName(), l.serialNumber(), l.quantity())).toList();
    }
}
