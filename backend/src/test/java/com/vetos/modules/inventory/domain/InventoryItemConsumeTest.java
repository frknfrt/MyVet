package com.vetos.modules.inventory.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Spec 2026-10-24 §3.3: once acik miktar, yetmezse yeni paket acilir. */
class InventoryItemConsumeTest {

    private static BigDecimal bd(String s) {
        return new BigDecimal(s);
    }

    private static InventoryItem item(StockUnit unit, String packageSize, int closed, String opened) {
        InventoryItem i = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Kalem", "İlaç", null,
            closed, 0, null, "L1", null);
        i.setStockUnit(unit, packageSize == null ? null : bd(packageSize), false);
        i.syncTo(closed, bd(opened));
        return i;
    }

    private static InventoryItem clavon(int closed, String opened) {
        return item(StockUnit.ML, "50", closed, opened);
    }

    @Test
    void ml_firstUse_opensBottle() {
        InventoryItem i = clavon(1, "0");
        StockChange c = i.consume(bd("1"), null);
        assertThat(c.closedDelta()).isEqualTo(-1);
        assertThat(c.openedDelta()).isEqualByComparingTo("49");
        assertThat(i.getQuantityOnHand()).isZero();
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("49");
    }

    @Test
    void ml_usesOpenBeforeClosed() {
        InventoryItem i = clavon(2, "49");
        i.consume(bd("1.5"), null);
        assertThat(i.getQuantityOnHand()).isEqualTo(2);
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("47.5");
    }

    @Test
    void ml_moreThanOpen_opensNext() {
        InventoryItem i = clavon(2, "10");
        i.consume(bd("30"), null);
        assertThat(i.getQuantityOnHand()).isEqualTo(1);
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("30");
    }

    @Test
    void ml_sourceWhole_opensNewEvenIfOpenExists() {
        InventoryItem i = clavon(2, "49");
        i.consume(bd("1"), StockSource.WHOLE);
        assertThat(i.getQuantityOnHand()).isEqualTo(1);
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("98");
    }

    @Test
    void vaccine_flakon_opensBox() {
        InventoryItem i = item(StockUnit.FLAKON, "20", 1, "0");
        i.consume(BigDecimal.ONE, null);
        assertThat(i.getQuantityOnHand()).isZero();
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("19");
    }

    @Test
    void adet_behavesAsToday() {
        InventoryItem i = item(StockUnit.ADET, null, 3, "0");
        StockChange c = i.consume(bd("2"), null);
        assertThat(c.closedDelta()).isEqualTo(-2);
        assertThat(c.openedDelta()).isEqualByComparingTo("0");
        assertThat(i.getQuantityOnHand()).isEqualTo(1);
    }

    // TARBİL esas: hekim TARBİL'de tablete "0,5" yazarsa onay düşümü hata vermemeli (değerlendirme C1); yukarı yuvarlanır.
    @Test
    void adet_roundsAFractionUp_soTarbilSettlementNeverFails() {
        InventoryItem i = item(StockUnit.ADET, null, 3, "0");
        assertThat(i.consume(bd("0.5"), null).closedDelta()).isEqualTo(-1);
        assertThat(i.consume(bd("1.2"), null).closedDelta()).isEqualTo(-2);
        assertThat(i.getQuantityOnHand()).isEqualTo(0);
    }

    @Test
    void consumeBeyondStock_opensPackageEvenWhenNoneClosed() {
        InventoryItem i = clavon(0, "2");
        i.consume(bd("5"), null);
        assertThat(i.getQuantityOnHand()).isEqualTo(-1);
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("47");
    }

    @Test
    void syncTo_unknownOpened_keepsOpened() {
        InventoryItem i = clavon(1, "49");
        StockChange c = i.syncTo(3, null);
        assertThat(i.getQuantityOnHand()).isEqualTo(3);
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("49");
        assertThat(c.closedDelta()).isEqualTo(2);
        assertThat(c.openedDelta()).isEqualByComparingTo("0");
    }

    @Test
    void newItem_defaultsToAdetWithNothingOpen() {
        InventoryItem i = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Kalem", null, null, 4, 0, null, null, null);
        assertThat(i.getUnit()).isEqualTo(StockUnit.ADET);
        assertThat(i.getOpenedQuantity()).isEqualByComparingTo("0");
        assertThat(i.isUnitManual()).isFalse();
    }
}
