package com.vetos.modules.inventory.infrastructure.persistence;

import com.vetos.modules.inventory.domain.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface InventoryItemJpaRepository extends JpaRepository<InventoryItem, UUID> {
    List<InventoryItem> findByBranchId(UUID branchId);
}
