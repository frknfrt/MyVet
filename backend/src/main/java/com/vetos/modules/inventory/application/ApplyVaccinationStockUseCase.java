package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Stoktan secilen asi (spec 2026-10-04 P2 S3.3): uygulaninca 1 OUT, iptalde (yalniz dusulmusse) 1 IN.
 * Ayni asi kaydi icin her yonde en fazla bir hareket; stok 0'da eksiye dusmez ve kayit yine olusur.
 */
@Service
@RequiredArgsConstructor
public class ApplyVaccinationStockUseCase {

    private final InventoryItemRepository items;
    private final StockMovementRepository movements;

    @Transactional
    public void administered(UUID vaccinationRecordId, UUID inventoryItemId) {
        if (inventoryItemId == null
            || movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.OUT)) {
            return;
        }
        items.findById(inventoryItemId).filter(i -> i.getQuantityOnHand() > 0).ifPresent(item -> {
            item.adjustQuantity(-1);
            items.save(item);
            movements.save(StockMovement.record(item.getTenantId(), item.getId(), StockMovementType.OUT, 1,
                StockReferenceType.VACCINATION, vaccinationRecordId));
        });
    }

    @Transactional
    public void cancelled(UUID vaccinationRecordId, UUID inventoryItemId) {
        if (inventoryItemId == null
            || !movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.OUT)
            || movements.existsByReference(vaccinationRecordId, StockReferenceType.VACCINATION, StockMovementType.IN)) {
            return;
        }
        items.findById(inventoryItemId).ifPresent(item -> {
            item.adjustQuantity(1);
            items.save(item);
            movements.save(StockMovement.record(item.getTenantId(), item.getId(), StockMovementType.IN, 1,
                StockReferenceType.VACCINATION, vaccinationRecordId));
        });
    }
}
