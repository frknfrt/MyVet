package com.vetos.modules.inventory.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record UpdateInventoryItemRequest(@NotBlank String name, String category, @PositiveOrZero int reorderThreshold, BigDecimal unitCost,
                                         /** null = birime dokunma; ADET | ML | G | FLAKON | TABLET (spec 2026-10-24/25) */ String unit,
                                         @com.vetos.platform.validation.StockAmount BigDecimal packageSize,
                                         /** ML kalemde bir pipetin ml'si ve adi (spec 2026-10-25). */
                                         @com.vetos.platform.validation.StockAmount BigDecimal pieceSize,
                                         @jakarta.validation.constraints.Size(max = 30) String pieceLabel) {}
