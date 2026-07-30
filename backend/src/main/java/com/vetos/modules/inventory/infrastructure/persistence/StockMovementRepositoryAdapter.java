package com.vetos.modules.inventory.infrastructure.persistence;

import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class StockMovementRepositoryAdapter implements StockMovementRepository {

    private final StockMovementJpaRepository jpaRepository;

    @Override
    public StockMovement save(StockMovement movement) { return jpaRepository.save(movement); }

    @Override
    public List<StockMovement> findByInventoryItemId(UUID inventoryItemId) { return jpaRepository.findByInventoryItemId(inventoryItemId); }
}
