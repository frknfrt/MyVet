package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.StockMatchKey;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;
import com.vetos.modules.inventory.domain.InventoryStockView;

import java.util.List;

/** Spec 2026-10-04 S13: lot (normalize) ayni VE urun adi kalemin TARBIL adi ya da adiyla ayni. Lotsuz satir NEW. */
final class StockSyncMatcher {

    record Match(TarbilStockSyncStatus status, InventoryStockView item) {}

    private StockSyncMatcher() {}

    static Match match(TarbilStockSnapshotLine line, List<InventoryStockView> stock) {
        if (line.isApplied()) {
            return new Match(TarbilStockSyncStatus.APPLIED,
                stock.stream().filter(i -> i.id().equals(line.getAppliedInventoryItemId())).findFirst().orElse(null));
        }
        String lot = StockMatchKey.of(line.getLotNumber());
        if (lot.isEmpty()) {
            return new Match(TarbilStockSyncStatus.NEW, null);
        }
        String product = StockMatchKey.of(line.getProductName());
        return stock.stream()
            .filter(i -> lot.equals(StockMatchKey.of(i.lotNumber())))
            .filter(i -> product.equals(StockMatchKey.of(i.tarbilProductName())) || product.equals(StockMatchKey.of(i.name())))
            .findFirst()
            .map(i -> new Match(i.quantityOnHand() == line.getQuantity() ? TarbilStockSyncStatus.MATCHED : TarbilStockSyncStatus.QUANTITY_DIFFERS, i))
            .orElse(new Match(TarbilStockSyncStatus.NEW, null));
    }
}
