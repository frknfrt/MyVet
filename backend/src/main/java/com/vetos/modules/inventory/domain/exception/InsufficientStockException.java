package com.vetos.modules.inventory.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class InsufficientStockException extends DomainException {
    public InsufficientStockException(UUID inventoryItemId, int requested, int available) {
        super(
            "INSUFFICIENT_STOCK",
            "Stok yetersiz: kalem=" + inventoryItemId + ", istenen=" + requested + ", mevcut=" + available
        );
    }
}
