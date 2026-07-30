package com.vetos.modules.inventory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository {
    InventoryItem save(InventoryItem item);
    Optional<InventoryItem> findById(UUID id);
    List<InventoryItem> findByBranchId(UUID branchId);
}
