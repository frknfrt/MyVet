package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.InventoryStockView;
import com.vetos.modules.inventory.domain.NewTarbilStockItem;
import com.vetos.modules.inventory.domain.PresentationUnitGuesser;
import com.vetos.modules.inventory.domain.StockChange;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class TarbilStockSyncAdapter implements TarbilStockSyncPort {

    private final InventoryItemRepository items;
    private final StockMovementRepository movements;
    private final com.vetos.modules.inventory.domain.PendingStockDeductionRepository pendingDeductions;

    @Override
    @Transactional(readOnly = true)
    public List<InventoryStockView> listForBranch(UUID branchId) {
        return items.findByBranchId(branchId).stream()
            .map(i -> new InventoryStockView(i.getId(), i.getName(), i.getLotNumber(), i.getExpiryDate(), i.getQuantityOnHand(),
                i.getTarbilProductName(), i.getOpenedQuantity(), i.getUnit().name()))
            .toList();
    }

    @Override
    @Transactional
    public UUID createFromTarbil(UUID branchId, NewTarbilStockItem n, UUID referenceId) {
        InventoryItem item = InventoryItem.create(
            TenantContext.current(), branchId, n.productName(), n.category(), null,
            0, 0, n.expiryDate(), n.lotNumber(), null
        );
        item.linkTarbil(n.tarbilSystem(), n.productName(), n.presentation());
        guessUnit(item, n.dosesPerPackage());
        StockChange change = item.syncTo(n.quantity(), n.openedQuantity() == null ? BigDecimal.ZERO : n.openedQuantity());
        item.setOpenedExpiry(n.openedExpiryDate());
        InventoryItem saved = items.save(item);
        record(saved, change, referenceId);
        return saved.getId();
    }

    @Override
    @Transactional
    public void syncFromTarbil(UUID inventoryItemId, int targetClosed, BigDecimal targetOpenedOrNull, Integer dosesPerPackage,
                               TarbilStockLink link, UUID referenceId, java.time.LocalDate openedExpiryDate) {
        InventoryItem item = items.findById(inventoryItemId).orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));
        item.linkTarbil(link.tarbilSystem(), link.productName(), link.presentation());
        guessUnit(item, dosesPerPackage);
        record(item, item.syncTo(targetClosed, targetOpenedOrNull), referenceId);
        if (targetOpenedOrNull != null) {
            item.setOpenedExpiry(openedExpiryDate);
        }
        items.save(item);
    }

    @Override
    @Transactional
    public void refreshFromSnapshot(UUID inventoryItemId, String tarbilSystem, String presentation, Integer dosesPerPackage,
                                    java.time.LocalDate openedExpiryDate) {
        InventoryItem item = items.findById(inventoryItemId).orElse(null);
        if (item == null) {
            return;
        }
        boolean expiryChanged = !java.util.Objects.equals(item.getOpenedExpiryDate(), openedExpiryDate);
        item.setOpenedExpiry(openedExpiryDate);
        expiryChanged = expiryChanged && java.util.Objects.equals(item.getOpenedExpiryDate(), openedExpiryDate);
        if (item.isUnitManual() || pendingDeductions.existsPendingForItem(inventoryItemId)) {
            if (expiryChanged) {
                items.save(item);
            }
            return;
        }
        String system = item.getTarbilSystem() != null ? item.getTarbilSystem() : tarbilSystem;
        String text = presentation != null ? presentation : item.getTarbilPresentation();
        var guess = PresentationUnitGuesser.guess(system, text, dosesPerPackage);
        if (guess.unit() == item.getUnit() && same(guess.packageSize(), item.getPackageSize()) && same(guess.pieceSize(), item.getPieceSize())) {
            if (expiryChanged) {
                items.save(item);
            }
            return;
        }
        item.setStockUnit(guess.unit(), guess.packageSize(), guess.pieceSize(), guess.pieceLabel(), false);
        items.save(item);
    }

    /** Elle ayarlanmis birime dokunulmaz (spec 2026-10-24 §3.4). */
    private static void guessUnit(InventoryItem item, Integer dosesPerPackage) {
        if (item.isUnitManual()) {
            return;
        }
        var guess = PresentationUnitGuesser.guess(item.getTarbilSystem(), item.getTarbilPresentation(), dosesPerPackage);
        item.setStockUnit(guess.unit(), guess.packageSize(), guess.pieceSize(), guess.pieceLabel(), false);
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** Tek TARBIL_SYNC hareketi: kapali fark (IN/OUT) + acik miktar farki. Hic fark yoksa hareket yazilmaz. */
    private void record(InventoryItem item, StockChange change, UUID referenceId) {
        int closed = change.closedDelta();
        BigDecimal opened = change.openedDelta();
        if (closed == 0 && opened.signum() == 0) {
            return;
        }
        boolean in = closed != 0 ? closed > 0 : opened.signum() > 0;
        movements.save(StockMovement.record(item.getTenantId(), item.getId(), in ? StockMovementType.IN : StockMovementType.OUT,
            Math.abs(closed), opened.signum() == 0 ? null : opened, StockReferenceType.TARBIL_SYNC, referenceId));
    }
}
