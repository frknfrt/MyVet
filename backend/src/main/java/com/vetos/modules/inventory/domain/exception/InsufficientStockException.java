package com.vetos.modules.inventory.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InsufficientStockException extends DomainException {
    public InsufficientStockException(String inventoryItemName, int requested, int available) {
        super(
            "INSUFFICIENT_STOCK",
            "Stok yetersiz: " + inventoryItemName + ", istenen=" + requested + ", mevcut=" + available
        );
    }
}
