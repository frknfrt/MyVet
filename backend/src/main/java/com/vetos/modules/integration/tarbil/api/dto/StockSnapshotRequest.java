package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.StockSnapshotLineInput;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/** partStockRead: asi sayfasinda Parca Stok tablosu okundu (spec 2026-10-24); eski eklenti gondermez: Parca Stok satiri varsa okunmus sayilir. */
public record StockSnapshotRequest(@NotNull TarbilStockSystem system, @NotNull @Size(min = 1, max = 500) List<@Valid StockSnapshotLineRequest> lines,
                                   Boolean partStockRead) {
    public List<StockSnapshotLineInput> toInputs() {
        return lines.stream().map(l -> new StockSnapshotLineInput(l.productName(), l.presentation(), l.lotNumber(),
            l.expiryDate(), l.quantity(), l.openedQuantity(), Boolean.TRUE.equals(l.partStock()), l.dosesPerPackage(), l.openedExpiryDate())).toList();
    }

    /** Eski eklenti bayrağı göndermez: Parça Stok satırı geldiyse tablo okunmuştur (değerlendirme I2). */
    public boolean isPartStockRead() {
        if (partStockRead != null) {
            return partStockRead;
        }
        return lines.stream().anyMatch(l -> Boolean.TRUE.equals(l.partStock()));
    }
}
