package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.*;
import com.vetos.modules.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
            StockMovement.record(item.getTenantId(), inventoryItemId, type, quantity, referenceType, referenceId)
        ).getId();
    }

    /**
     * Kullanim dusumu (spec 2026-10-24 §3.3): kalemin kuralina gore kapali adet ve/veya acik miktar duser; tek OUT hareketi
     * (quantity = acilan kapali adet, openedDelta = acik miktar farki) yazilir.
     */
    @Transactional
    public StockChange consume(UUID inventoryItemId, BigDecimal amount, StockSource source, StockReferenceType referenceType, UUID referenceId) {
        InventoryItem item = inventoryItemRepository.findById(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));
        StockChange change = item.consume(amount, source);
        if (item.getQuantityOnHand() < 0) {
            log.warn("Stok kalemi negatife dustu: itemId={}, quantityOnHand={}", inventoryItemId, item.getQuantityOnHand());
        }
        inventoryItemRepository.save(item);
        stockMovementRepository.save(StockMovement.record(item.getTenantId(), inventoryItemId, StockMovementType.OUT,
            -change.closedDelta(), change.openedDelta().signum() == 0 ? null : change.openedDelta(), referenceType, referenceId));
        return change;
    }
}
