package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * @docs/implementation-plan.md Modul 6: EncounterFinalizedEvent dinleyicisi
 * -> kullanilan malzemenin otomatik stoktan dusumu.
 */
@Service
@RequiredArgsConstructor
public class AutoDeductStockForEncounterUseCase {

    private final RecordStockMovementUseCase recordStockMovementUseCase;

    public record UsedItem(UUID inventoryItemId, int quantity) {}

    @Transactional
    public void execute(UUID encounterId, List<UsedItem> usedItems) {
        for (UsedItem used : usedItems) {
            recordStockMovementUseCase.execute(
                used.inventoryItemId(), StockMovementType.OUT, used.quantity(), StockReferenceType.ENCOUNTER, encounterId
            );
        }
    }
}
