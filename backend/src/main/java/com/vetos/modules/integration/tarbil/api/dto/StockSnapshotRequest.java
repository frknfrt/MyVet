package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.StockSnapshotLineInput;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record StockSnapshotRequest(@NotNull TarbilStockSystem system, @NotNull @Size(min = 1, max = 500) List<@Valid StockSnapshotLineRequest> lines) {
    public List<StockSnapshotLineInput> toInputs() {
        return lines.stream().map(l -> new StockSnapshotLineInput(l.productName(), l.presentation(), l.lotNumber(),
            l.expiryDate(), l.quantity(), l.openedQuantity())).toList();
    }
}
