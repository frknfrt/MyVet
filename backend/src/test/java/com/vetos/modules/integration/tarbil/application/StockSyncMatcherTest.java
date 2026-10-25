package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSystem;
import java.math.BigDecimal;
import com.vetos.modules.inventory.domain.InventoryStockView;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class StockSyncMatcherTest {

    private static TarbilStockSnapshotLine line(String product, String lot, int qty) {
        return TarbilStockSnapshotLine.of(UUID.randomUUID(), UUID.randomUUID(), 1, product, null, lot, null, qty, null);
    }

    private static InventoryStockView item(String name, String lot, int qty, String tarbilName) {
        return new InventoryStockView(UUID.randomUUID(), name, lot, null, qty, tarbilName, java.math.BigDecimal.ZERO, "ADET");
    }

    @Test
    void should_beNew_when_noItemHasThatLot() {
        assertThat(StockSyncMatcher.match(line("Drontal", "L1", 3), List.of(item("Drontal", "L2", 3, null)), new StockSyncMatcher.Target(3, null, null)).status())
            .isEqualTo(TarbilStockSyncStatus.NEW);
    }

    @Test
    void should_match_when_lotAndNameEqualIgnoringCaseAndSpaces() {
        var m = StockSyncMatcher.match(line(" drontal  plus ", "l1", 3), List.of(item("DRONTAL PLUS", "L1", 3, null)), new StockSyncMatcher.Target(3, null, null));
        assertThat(m.status()).isEqualTo(TarbilStockSyncStatus.MATCHED);
        assertThat(m.item()).isNotNull();
    }

    @Test
    void should_reportQuantityDifference_when_matchedByTarbilProductName() {
        var m = StockSyncMatcher.match(line("Rabisin", "R9", 10), List.of(item("Kuduz aşısı", "R9", 4, "Rabisin")), new StockSyncMatcher.Target(10, null, null));
        assertThat(m.status()).isEqualTo(TarbilStockSyncStatus.QUANTITY_DIFFERS);
    }

    @Test
    void should_compareAgainstGroupTotal_when_sameLotSpansSeveralRows() {
        var m = StockSyncMatcher.match(line("Drontal", "L1", 3), List.of(item("Drontal", "L1", 8, null)), new StockSyncMatcher.Target(8, null, null));
        assertThat(m.status()).isEqualTo(TarbilStockSyncStatus.MATCHED);
    }

    @Test
    void should_matchLotlessItemByName_when_lineHasNoLot() {
        var m = StockSyncMatcher.match(line("Drontal", null, 3), List.of(item("Drontal", null, 3, null)), new StockSyncMatcher.Target(3, null, null));
        assertThat(m.status()).isEqualTo(TarbilStockSyncStatus.MATCHED);
    }

    @Test
    void should_notMatchLottedItem_when_lineHasNoLot() {
        assertThat(StockSyncMatcher.match(line("Drontal", null, 3), List.of(item("Drontal", "L1", 3, null)), new StockSyncMatcher.Target(3, null, null)).status())
            .isEqualTo(TarbilStockSyncStatus.NEW);
    }

    @Test
    void should_groupLinesByProductAndLot() {
        var a = line("Drontal", "L1", 3);
        var b = line(" DRONTAL ", "l1", 5);
        var c = line("Drontal", null, 2);
        var targets = StockSyncMatcher.targets(TarbilStockSystem.VETILAC_MEDICINE, false, List.of(a, b, c));
        assertThat(targets.get(StockSyncMatcher.key(a)).closed()).isEqualTo(8);
        assertThat(targets.get(StockSyncMatcher.key(c)).closed()).isEqualTo(2);
    }

    @Test
    void should_beApplied_when_lineWasAlreadyApplied() {
        TarbilStockSnapshotLine l = line("Drontal", "L1", 3);
        l.markApplied(UUID.randomUUID(), Instant.now());
        assertThat(StockSyncMatcher.match(l, List.of(), new StockSyncMatcher.Target(3, null, null)).status()).isEqualTo(TarbilStockSyncStatus.APPLIED);
    }

    private static TarbilStockSnapshotLine vaccine(String product, String lot, int qty, boolean part, Integer doses) {
        return TarbilStockSnapshotLine.of(UUID.randomUUID(), UUID.randomUUID(), 1, product, null, lot, null, qty, null, part, doses);
    }

    private static InventoryStockView stock(String name, String lot, int closed, String opened) {
        return new InventoryStockView(UUID.randomUUID(), name, lot, null, closed, null, new BigDecimal(opened), "ML");
    }

    @Test
    void vaccinePartStock_isOpenedNotClosed() {
        var l = vaccine("Biocan R", "665932", 18, true, null);
        var t = StockSyncMatcher.targets(TarbilStockSystem.HBSAPP_VACCINE, true, List.of(l)).get(StockSyncMatcher.key(l));
        assertThat(t.closed()).isZero();
        assertThat(t.opened()).isEqualByComparingTo("18");
        assertThat(t.dosesPerPackage()).isNull();
    }

    @Test
    void vaccineWholeAndPart_areNotSummed() {
        var whole = vaccine("Biocan DHPPI", "145932", 1, false, 20);
        var part = vaccine("Biocan DHPPI", "145932", 19, true, null);
        var t = StockSyncMatcher.targets(TarbilStockSystem.HBSAPP_VACCINE, true, List.of(whole, part)).get(StockSyncMatcher.key(whole));
        assertThat(t).isEqualTo(new StockSyncMatcher.Target(1, new BigDecimal("19"), 20));
    }

    @Test
    void vaccineWithoutPartStockRead_hasUnknownOpened() {
        var whole = vaccine("Biocan DHPPI", "145932", 1, false, 20);
        assertThat(StockSyncMatcher.targets(TarbilStockSystem.HBSAPP_VACCINE, false, List.of(whole)).get(StockSyncMatcher.key(whole)).opened()).isNull();
    }

    @Test
    void medicineOpened_isCompared() {
        var l = TarbilStockSnapshotLine.of(UUID.randomUUID(), UUID.randomUUID(), 1, "CLAVON = Clavon", null, "63016", null, 0, new BigDecimal("49.000"));
        var t = StockSyncMatcher.targets(TarbilStockSystem.VETILAC_MEDICINE, false, List.of(l)).get(StockSyncMatcher.key(l));
        assertThat(t.opened()).isEqualByComparingTo("49");
        assertThat(StockSyncMatcher.match(l, List.of(stock("CLAVON = Clavon", "63016", 1, "0")), t).status()).isEqualTo(TarbilStockSyncStatus.QUANTITY_DIFFERS);
        assertThat(StockSyncMatcher.match(l, List.of(stock("CLAVON = Clavon", "63016", 0, "49")), t).status()).isEqualTo(TarbilStockSyncStatus.MATCHED);
    }
}
