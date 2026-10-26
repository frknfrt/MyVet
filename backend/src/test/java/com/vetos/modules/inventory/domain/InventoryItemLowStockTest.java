package com.vetos.modules.inventory.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Düşük stok açık miktarı kutu karşılığıyla sayar (spec 2026-10-25 §4.3/11). */
class InventoryItemLowStockTest {

    private static InventoryItem vaccine(int closed, String opened, int threshold) {
        InventoryItem i = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Biocan R", "Aşı", null,
            closed, threshold, null, "L1", null);
        i.setStockUnit(StockUnit.FLAKON, new BigDecimal("20"), false);
        i.syncTo(closed, new BigDecimal(opened));
        return i;
    }

    @Test
    void lowStock_countsOpenedAsBoxFraction() {
        assertThat(vaccine(0, "19", 0).isBelowReorderThreshold()).isFalse();
        assertThat(vaccine(0, "19", 1).isBelowReorderThreshold()).isTrue();
        assertThat(vaccine(1, "0", 1).isBelowReorderThreshold()).isTrue();
    }

    @Test
    void adet_keepsClosedOnlyRule() {
        InventoryItem i = InventoryItem.create(UUID.randomUUID(), UUID.randomUUID(), "Tasma", "Aksesuar", null, 2, 2, null, null, null);
        assertThat(i.isBelowReorderThreshold()).isTrue();
    }
}
