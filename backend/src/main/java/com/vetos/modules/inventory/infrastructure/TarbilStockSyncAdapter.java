package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.NewTarbilStockItem;
import com.vetos.modules.inventory.domain.StockMovement;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.TarbilStockLink;
import com.vetos.modules.inventory.domain.TarbilStockSyncPort;
import com.vetos.modules.inventory.domain.exception.InventoryItemNotFoundException;
import com.vetos.platform.tenancy.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilStockSyncAdapter implements TarbilStockSyncPort {

    private final InventoryItemRepository items;
    private final StockMovementRepository movements;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryStockView> listForBranch(UUID branchId) {
        return items.findByBranchId(branchId).stream()
            .map(i -> new InventoryStockView(i.getId(), i.getName(), i.getLotNumber(), i.getExpiryDate(), i.getQuantityOnHand(), i.getTarbilProductName()))
            .toList();
    }

    @Override
    @Transactional
    public UUID createFromTarbil(UUID branchId, NewTarbilStockItem n, UUID referenceId) {
        InventoryItem item = InventoryItem.create(
            TenantContext.current(), branchId, n.productName(), n.category(), null,
            n.quantity(), 0, n.expiryDate(), n.lotNumber(), null
        );
        item.linkTarbil(n.tarbilSystem(), n.productName(), n.presentation());
        InventoryItem saved = items.save(item);
        if (n.quantity() > 0) {
            movements.save(StockMovement.record(saved.getTenantId(), saved.getId(), StockMovementType.IN, n.quantity(),
                StockReferenceType.TARBIL_SYNC, referenceId));
        }
        return saved.getId();
    }

    @Override
    @Transactional
    public void syncFromTarbil(UUID inventoryItemId, int targetQuantity, TarbilStockLink link, UUID referenceId) {
        InventoryItem item = items.findById(inventoryItemId).orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));
        item.linkTarbil(link.tarbilSystem(), link.productName(), link.presentation());
        int delta = targetQuantity - item.getQuantityOnHand();
        if (delta != 0) {
            item.adjustQuantity(delta);
            movements.save(StockMovement.record(item.getTenantId(), inventoryItemId,
                delta > 0 ? StockMovementType.IN : StockMovementType.OUT, Math.abs(delta), StockReferenceType.TARBIL_SYNC, referenceId));
        }
        items.save(item);
    }
}
