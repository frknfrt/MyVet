package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilConfirmationMethod;
import com.vetos.modules.inventory.domain.ReportedDeductionLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** lines: eklentinin TARBIL'de onaylanan urun satirlari (spec 2026-10-09 S5.3); MANUAL'da yok sayilir. */
public record MarkSubmittedRequest(@NotNull TarbilConfirmationMethod method, @Size(max = 100) String tarbilReference,
                                   @Valid @Size(max = 50) List<Line> lines) {

    /**
     * quantity: TARBIL "Kullanilacak Adet" (paketli kalemde 0); amount: "Kullanilacak Miktari"; source: "Stok Tipi" (WHOLE | OPEN).
     * Eski eklenti yalniz quantity >= 1 gonderir (spec 2026-10-24).
     */
    public record Line(@NotBlank @Size(max = 300) String productName, @Size(max = 100) String serialNumber, @PositiveOrZero int quantity,
                       @Positive java.math.BigDecimal amount, @Size(max = 10) String source) {
        @com.fasterxml.jackson.annotation.JsonIgnore
        @jakarta.validation.constraints.AssertTrue(message = "Adet ya da miktar dolu olmalı")
        public boolean isQuantityOrAmountGiven() {
            return quantity > 0 || amount != null;
        }

        java.math.BigDecimal effectiveAmount() {
            return amount != null ? amount : java.math.BigDecimal.valueOf(quantity);
        }

        com.vetos.modules.inventory.domain.StockSource stockSource() {
            return "WHOLE".equals(source) ? com.vetos.modules.inventory.domain.StockSource.WHOLE
                : "OPEN".equals(source) ? com.vetos.modules.inventory.domain.StockSource.OPEN : null;
        }
    }

    public List<ReportedDeductionLine> toReported() {
        return lines == null ? null : lines.stream().map(l -> new ReportedDeductionLine(l.productName(), l.serialNumber(), l.effectiveAmount(), l.stockSource())).toList();
    }
}
