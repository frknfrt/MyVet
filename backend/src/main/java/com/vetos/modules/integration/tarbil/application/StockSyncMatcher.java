package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.StockMatchKey;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import com.vetos.modules.inventory.domain.InventoryStockView;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Spec 2026-10-04 S13: lot (normalize) ayni VE urun adi kalemin TARBIL adi ya da adiyla ayni. Lotsuz satir yalniz
 * lotsuz kalemle (ad ile) eslesir. TARBIL ayni urun+lotu birden cok satirda (satis yeri / stok tipi) gosterebilir:
 * bunlar tek kaleme karsilik gelir ve urun+lot toplamiyla (Target) karsilastirilir.
 * Spec 2026-10-24: hedef iki parcali -- kapali adet ve acik miktar. Ilacta "Acilmis Kalan Miktar" kolonu; asida "Parca
 * Stok" tablosu (yalniz eklenti o tabloyu okuyabildiyse; yoksa acik miktar bilinmiyor = null).
 */
final class StockSyncMatcher {

    record Match(TarbilStockSyncStatus status, InventoryStockView item) {}

    /** opened null: TARBIL'deki acik/parca miktar bilinmiyor (Vetly'deki degismez). */
    record Target(int closed, BigDecimal opened, Integer dosesPerPackage) {}

    private StockSyncMatcher() {}

    static String key(TarbilStockSnapshotLine line) {
        return StockMatchKey.of(line.getProductName()) + "\u0000" + StockMatchKey.of(line.getLotNumber());
    }

    static Map<String, Target> targets(TarbilStockSystem system, boolean partStockRead, List<TarbilStockSnapshotLine> lines) {
        boolean vaccine = system == TarbilStockSystem.HBSAPP_VACCINE;
        Map<String, Target> targets = new HashMap<>();
        for (TarbilStockSnapshotLine l : lines) {
            Target t = targets.getOrDefault(key(l), new Target(0, vaccine && !partStockRead ? null : BigDecimal.ZERO, null));
            int closed = t.closed();
            BigDecimal opened = t.opened();
            Integer doses = t.dosesPerPackage();
            if (vaccine && l.isPartStock()) {
                opened = opened == null ? null : opened.add(BigDecimal.valueOf(l.getQuantity()));
            } else {
                closed += l.getQuantity();
                if (!vaccine && l.getOpenedQuantity() != null) {
                    opened = opened.add(l.getOpenedQuantity());
                }
                if (doses == null) {
                    doses = l.getDosesPerPackage();
                }
            }
            targets.put(key(l), new Target(closed, opened, doses));
        }
        return targets;
    }

    static Match match(TarbilStockSnapshotLine line, List<InventoryStockView> stock, Target target) {
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
            .map(i -> new Match(same(i, target) ? TarbilStockSyncStatus.MATCHED : TarbilStockSyncStatus.QUANTITY_DIFFERS, i))
            .orElse(new Match(TarbilStockSyncStatus.NEW, null));
    }

    private static boolean same(InventoryStockView item, Target target) {
        if (item.quantityOnHand() != target.closed()) {
            return false;
        }
        if (target.opened() == null) {
            return true;
        }
        BigDecimal opened = item.openedQuantity() == null ? BigDecimal.ZERO : item.openedQuantity();
        return opened.compareTo(target.opened()) == 0;
    }
}
