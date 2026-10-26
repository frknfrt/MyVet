package com.vetos.modules.inventory.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.vetos.modules.inventory.domain.StockMovementType;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/**
 * Elle stok hareketi. quantity: kapali adet; openedDelta: acik miktar farki (isaretli, en cok 2 ondalik; spec 2026-10-25
 * §4.4/14). En az biri dolu olmali.
 */
public record StockMovementRequest(@NotNull StockMovementType movementType, @PositiveOrZero int quantity,
                                   @Digits(integer = 6, fraction = 2) @jakarta.validation.constraints.DecimalMin("-100000")
                                   @jakarta.validation.constraints.DecimalMax("100000") BigDecimal openedDelta) {

    public StockMovementRequest(StockMovementType movementType, int quantity) {
        this(movementType, quantity, null);
    }

    @JsonIgnore
    @AssertTrue(message = "Adet ya da açık miktar farkı girilmeli")
    public boolean isSomeChangeGiven() {
        return quantity > 0 || (openedDelta != null && openedDelta.signum() != 0);
    }
}
