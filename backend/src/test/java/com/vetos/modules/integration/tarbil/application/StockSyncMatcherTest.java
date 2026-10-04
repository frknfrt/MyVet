package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilStockSnapshotLine;
import com.vetos.modules.integration.tarbil.domain.TarbilStockSyncStatus;
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
        return new InventoryStockView(UUID.randomUUID(), name, lot, null, qty, tarbilName);
    }

    @Test
    void should_beNew_when_noItemHasThatLot() {
        assertThat(StockSyncMatcher.match(line("Drontal", "L1", 3), List.of(item("Drontal", "L2", 3, null)), 3).status())
            .isEqualTo(TarbilStockSyncStatus.NEW);
    }

    @Test
    void should_match_when_lotAndNameEqualIgnoringCaseAndSpaces() {
        var m = StockSyncMatcher.match(line(" drontal  plus ", "l1", 3), List.of(item("DRONTAL PLUS", "L1", 3, null)), 3);
        assertThat(m.status()).isEqualTo(TarbilStockSyncStatus.MATCHED);
        assertThat(m.item()).isNotNull();
    }

    @Test
    void should_reportQuantityDifference_when_matchedByTarbilProductName() {
        var m = StockSyncMatcher.match(line("Rabisin", "R9", 10), List.of(item("Kuduz aşısı", "R9", 4, "Rabisin")), 10);
        assertThat(m.status()).isEqualTo(TarbilStockSyncStatus.QUANTITY_DIFFERS);
    }

    @Test
    void should_compareAgainstGroupTotal_when_sameLotSpansSeveralRows() {
        var m = StockSyncMatcher.match(line("Drontal", "L1", 3), List.of(item("Drontal", "L1", 8, null)), 8);
        assertThat(m.status()).isEqualTo(TarbilStockSyncStatus.MATCHED);
    }

    @Test
    void should_matchLotlessItemByName_when_lineHasNoLot() {
        var m = StockSyncMatcher.match(line("Drontal", null, 3), List.of(item("Drontal", null, 3, null)), 3);
        assertThat(m.status()).isEqualTo(TarbilStockSyncStatus.MATCHED);
    }

    @Test
    void should_notMatchLottedItem_when_lineHasNoLot() {
        assertThat(StockSyncMatcher.match(line("Drontal", null, 3), List.of(item("Drontal", "L1", 3, null)), 3).status())
            .isEqualTo(TarbilStockSyncStatus.NEW);
    }

    @Test
    void should_groupLinesByProductAndLot() {
        var a = line("Drontal", "L1", 3);
        var b = line(" DRONTAL ", "l1", 5);
        var c = line("Drontal", null, 2);
        var totals = StockSyncMatcher.totals(List.of(a, b, c));
        assertThat(totals.get(StockSyncMatcher.key(a))).isEqualTo(8);
        assertThat(totals.get(StockSyncMatcher.key(c))).isEqualTo(2);
    }

    @Test
    void should_beApplied_when_lineWasAlreadyApplied() {
        TarbilStockSnapshotLine l = line("Drontal", "L1", 3);
        l.markApplied(UUID.randomUUID(), Instant.now());
        assertThat(StockSyncMatcher.match(l, List.of(), 3).status()).isEqualTo(TarbilStockSyncStatus.APPLIED);
    }
}
