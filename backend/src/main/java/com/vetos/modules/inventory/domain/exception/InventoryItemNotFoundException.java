package com.vetos.modules.inventory.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class InventoryItemNotFoundException extends DomainException {
    public InventoryItemNotFoundException(UUID id) {
        super("INVENTORY_ITEM_NOT_FOUND", "Stok kalemi bulunamadi: " + id);
    }
}
