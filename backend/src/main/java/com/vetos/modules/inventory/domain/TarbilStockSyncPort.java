package com.vetos.modules.inventory.domain;

import java.util.List;
import java.util.UUID;

/**
 * integration/tarbil icin (spec 2026-10-04 S13): TARBIL stok anlik goruntusunu Vetly stoguna esitler.
 * Kiraci TenantContext'ten gelir (cagiran kimligi dogrulanmis bir istek icindedir).
 */
public interface TarbilStockSyncPort {
    List<InventoryStockView> listForBranch(UUID branchId);

    /** Yeni kalem + IN hareketi (TARBIL_SYNC). */
    UUID createFromTarbil(UUID branchId, NewTarbilStockItem item, UUID referenceId);

    /** Miktari TARBIL'deki degere getirir (fark kadar IN/OUT, TARBIL_SYNC) ve TARBIL baglantisini yazar. */
    void syncFromTarbil(UUID inventoryItemId, int targetQuantity, TarbilStockLink link, UUID referenceId);
}
