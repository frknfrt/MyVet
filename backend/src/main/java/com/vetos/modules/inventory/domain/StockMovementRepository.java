package com.vetos.modules.inventory.domain;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository {
    StockMovement save(StockMovement movement);
    List<StockMovement> findByInventoryItemId(UUID inventoryItemId);

    /** Ayni kaynak (ornegin asi kaydi) icin bu turde hareket zaten yazildi mi -- cift olaya karsi. */
    boolean existsByReference(UUID referenceId, StockReferenceType referenceType, StockMovementType movementType);
}
