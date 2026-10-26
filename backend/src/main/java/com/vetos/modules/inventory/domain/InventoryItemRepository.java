package com.vetos.modules.inventory.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InventoryItemRepository {
    InventoryItem save(InventoryItem item);
    Optional<InventoryItem> findById(UUID id);

    /** Satir kilidiyle ve veritabanindan taze okur (es zamanli dusum, spec 2026-10-25 §4.3/8). */
    Optional<InventoryItem> findByIdForUpdate(UUID id);
    List<InventoryItem> findByBranchId(UUID branchId);
}
