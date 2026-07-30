package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.*;
import com.vetos.modules.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class RecordStockMovementUseCase {

    private final InventoryItemRepository inventoryItemRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional
    public UUID execute(UUID inventoryItemId, StockMovementType type, int quantity, StockReferenceType referenceType, UUID referenceId) {
        InventoryItem item = inventoryItemRepository.findById(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));

        int delta = type == StockMovementType.OUT ? -quantity : quantity;
        item.adjustQuantity(delta);
        if (item.getQuantityOnHand() < 0) {
            log.warn("Stok kalemi negatife dustu: itemId={}, quantityOnHand={}", inventoryItemId, item.getQuantityOnHand());
        }
        inventoryItemRepository.save(item);

        return stockMovementRepository.save(
            StockMovement.record(inventoryItemId, type, quantity, referenceType, referenceId)
        ).getId();
    }
}
