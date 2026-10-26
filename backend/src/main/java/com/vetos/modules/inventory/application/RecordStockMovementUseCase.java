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
        InventoryItem item = inventoryItemRepository.findByIdForUpdate(inventoryItemId)
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
     * Elle stok hareketi tek islemde (degerlendirme I3): once acik miktar farki dogrulanip uygulanir, sonra kapali adet;
     * biri gecersizse hicbiri yazilmaz.
     */
    @Transactional
    public void recordManual(UUID inventoryItemId, StockMovementType type, int quantity, BigDecimal openedDelta) {
        InventoryItem item = inventoryItemRepository.findByIdForUpdate(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));
        boolean opened = openedDelta != null && openedDelta.signum() != 0;
        StockChange openedChange = opened ? item.adjustOpened(openedDelta) : null;
        if (quantity > 0) {
            item.adjustQuantity(type == StockMovementType.OUT ? -quantity : quantity);
        }
        inventoryItemRepository.save(item);
        if (quantity > 0) {
            stockMovementRepository.save(StockMovement.record(item.getTenantId(), inventoryItemId, type, quantity, StockReferenceType.MANUAL, null));
        }
        if (opened) {
            stockMovementRepository.save(StockMovement.record(item.getTenantId(), inventoryItemId, StockMovementType.ADJUSTMENT,
                0, openedChange.openedDelta(), StockReferenceType.MANUAL, null));
        }
    }

    /** Elle acik miktar duzeltmesi: tek ADJUSTMENT hareketi (quantity 0, openedDelta = fark; spec 2026-10-25 §4.4/14). */
    @Transactional
    public StockChange adjustOpened(UUID inventoryItemId, BigDecimal openedDelta) {
        InventoryItem item = inventoryItemRepository.findByIdForUpdate(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));
        StockChange change = item.adjustOpened(openedDelta);
        inventoryItemRepository.save(item);
        stockMovementRepository.save(StockMovement.record(item.getTenantId(), inventoryItemId, StockMovementType.ADJUSTMENT,
            0, change.openedDelta(), StockReferenceType.MANUAL, null));
        return change;
    }

    /**
     * Kullanim dusumu (spec 2026-10-24 §3.3): kalemin kuralina gore kapali adet ve/veya acik miktar duser; tek OUT hareketi
     * (quantity = acilan kapali adet, openedDelta = acik miktar farki) yazilir.
     */
    @Transactional
    public StockChange consume(UUID inventoryItemId, BigDecimal amount, StockSource source, StockReferenceType referenceType, UUID referenceId) {
        InventoryItem item = inventoryItemRepository.findByIdForUpdate(inventoryItemId)
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
