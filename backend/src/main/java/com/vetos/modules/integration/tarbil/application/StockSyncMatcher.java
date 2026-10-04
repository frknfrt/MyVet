package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.StockMatchKey;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;
import com.vetos.modules.inventory.domain.InventoryStockView;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec 2026-10-04 S13: lot (normalize) ayni VE urun adi kalemin TARBIL adi ya da adiyla ayni. Lotsuz satir yalniz
 * lotsuz kalemle (ad ile) eslesir. TARBIL ayni urun+lotu birden cok satirda (satis yeri / stok tipi) gosterebilir:
 * bunlar tek kaleme karsilik gelir, miktar toplami (targetQuantity) ile karsilastirilir.
 */
final class StockSyncMatcher {

    record Match(TarbilStockSyncStatus status, InventoryStockView item) {}

    private StockSyncMatcher() {}

    static String key(TarbilStockSnapshotLine line) {
        return StockMatchKey.of(line.getProductName()) + "\u0000" + StockMatchKey.of(line.getLotNumber());
    }

    static Map<String, Integer> totals(List<TarbilStockSnapshotLine> lines) {
        Map<String, Integer> totals = new HashMap<>();
        lines.forEach(l -> totals.merge(key(l), l.getQuantity(), Integer::sum));
        return totals;
    }

    static Match match(TarbilStockSnapshotLine line, List<InventoryStockView> stock, int targetQuantity) {
        if (line.isApplied()) {
            return new Match(TarbilStockSyncStatus.APPLIED,
                stock.stream().filter(i -> i.id().equals(line.getAppliedInventoryItemId())).findFirst().orElse(null));
        }
        String lot = StockMatchKey.of(line.getLotNumber());
        String product = StockMatchKey.of(line.getProductName());
        return stock.stream()
            .filter(i -> lot.equals(StockMatchKey.of(i.lotNumber())))
            .filter(i -> product.equals(StockMatchKey.of(i.tarbilProductName())) || product.equals(StockMatchKey.of(i.name())))
            .findFirst()
            .map(i -> new Match(i.quantityOnHand() == targetQuantity ? TarbilStockSyncStatus.MATCHED : TarbilStockSyncStatus.QUANTITY_DIFFERS, i))
            .orElse(new Match(TarbilStockSyncStatus.NEW, null));
    }
}
