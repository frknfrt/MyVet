package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.StockUnit;
import com.vetos.modules.inventory.domain.exception.InvalidStockUnitException;
import com.vetos.modules.inventory.domain.exception.StockUnitPendingConflictException;
import com.vetos.modules.inventory.domain.PendingStockDeductionRepository;
import java.math.BigDecimal;

import com.vetos.modules.inventory.application.dto.UpdateInventoryItemCommand;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateInventoryItemUseCase {

    private final InventoryItemRepository inventoryItemRepository;
    private final PendingStockDeductionRepository pendingDeductions;

    @Transactional
    public void execute(UpdateInventoryItemCommand command) {
        InventoryItem item = inventoryItemRepository.findById(command.itemId())
            .orElseThrow(() -> new InventoryItemNotFoundException(command.itemId()));
        item.updateDetails(command.name(), command.category(), command.reorderThreshold(), command.unitCost());
        if (command.unit() != null) {
            StockUnit unit = parseUnit(command.unit(), command.packageSize());
            // Bekleyen düşümün miktarı birimle okunur: birim/paket değişikliği o kayıt bitene kadar kilitli (değerlendirme I3).
            if (unitChanges(item, unit, command.packageSize()) && pendingDeductions.existsPendingForItem(command.itemId())) {
                throw new StockUnitPendingConflictException();
            }
            item.setStockUnit(unit, command.packageSize(), true);
        }
        inventoryItemRepository.save(item);
    }

    private static boolean unitChanges(InventoryItem item, StockUnit unit, BigDecimal packageSize) {
        if (item.getUnit() != unit) {
            return true;
        }
        BigDecimal current = item.getPackageSize();
        return unit != StockUnit.ADET && (current == null || packageSize == null || current.compareTo(packageSize) != 0);
    }

    /** Elle ayar (spec 2026-10-24): paketli birimde paket buyuklugu zorunlu. */
    private static StockUnit parseUnit(String unit, BigDecimal packageSize) {
        StockUnit parsed;
        try {
            parsed = StockUnit.valueOf(unit);
        } catch (IllegalArgumentException e) {
            throw new InvalidStockUnitException();
        }
        if (parsed != StockUnit.ADET && (packageSize == null || packageSize.signum() <= 0)) {
            throw new InvalidStockUnitException();
        }
        return parsed;
    }
}
