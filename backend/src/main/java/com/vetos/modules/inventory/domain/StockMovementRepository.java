package com.vetos.modules.inventory.domain;

import java.util.List;
import java.util.UUID;

public interface StockMovementRepository {
    StockMovement save(StockMovement movement);
    List<StockMovement> findByInventoryItemId(UUID inventoryItemId);
}
