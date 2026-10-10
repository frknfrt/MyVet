package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * @docs/implementation-plan.md Modul 6: EncounterFinalizedEvent dinleyicisi
 * -> kullanilan malzemenin otomatik stoktan dusumu. TARBIL'e bagli kalem (tarbil_product_name dolu) TARBIL
 * dusumunu bekler (spec 2026-10-09 S5.1); digerleri hemen duser.
 */
@Service
@RequiredArgsConstructor
public class AutoDeductStockForEncounterUseCase {

    private final RecordStockMovementUseCase recordStockMovementUseCase;
    private final PendingDeductionService pendingDeductionService;
    private final InventoryItemRepository inventoryItemRepository;

    public record UsedItem(UUID inventoryItemId, int quantity) {}

    @Transactional
    public void execute(UUID encounterId, List<UsedItem> usedItems) {
        for (UsedItem used : usedItems) {
            boolean linked = inventoryItemRepository.findById(used.inventoryItemId())
                .map(i -> i.getTarbilProductName() != null).orElse(false);
            if (linked) {
                pendingDeductionService.open(used.inventoryItemId(), used.quantity(), StockReferenceType.ENCOUNTER, encounterId);
            } else {
                recordStockMovementUseCase.execute(
                    used.inventoryItemId(), StockMovementType.OUT, used.quantity(), StockReferenceType.ENCOUNTER, encounterId
                );
            }
        }
    }
}
