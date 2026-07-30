package com.vetos.modules.inventory.infrastructure.persistence;

import com.vetos.modules.inventory.domain.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface StockMovementJpaRepository extends JpaRepository<StockMovement, UUID> {
    List<StockMovement> findByInventoryItemId(UUID inventoryItemId);
}
