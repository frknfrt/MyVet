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

    public record UsedItem(UUID inventoryItemId, java.math.BigDecimal quantity) {}

    @Transactional
    public void execute(UUID encounterId, List<UsedItem> usedItems) {
        for (UsedItem raw : usedItems) {
            var item = inventoryItemRepository.findById(raw.inventoryItemId());
            boolean linked = item.map(i -> i.getTarbilProductName() != null).orElse(false);
            // Adet ve tablet ile kullanilan kalemde ondalik miktar yukari yuvarlanir: muayene kapanisi hicbir zaman takilmasin
            // (spec 2026-10-24/25; muayene modulu stok birimini goremez, ekran tam sayi ister).
            boolean whole = item.map(i -> !i.isOpenable() || i.getUnit() == com.vetos.modules.inventory.domain.StockUnit.TABLET).orElse(true);
            UsedItem used = whole ? new UsedItem(raw.inventoryItemId(), raw.quantity().setScale(0, java.math.RoundingMode.CEILING)) : raw;
            if (linked) {
                pendingDeductionService.open(used.inventoryItemId(), used.quantity(), StockReferenceType.ENCOUNTER, encounterId);
            } else {
                recordStockMovementUseCase.consume(used.inventoryItemId(), used.quantity(), null, StockReferenceType.ENCOUNTER, encounterId);
            }
        }
    }
}
