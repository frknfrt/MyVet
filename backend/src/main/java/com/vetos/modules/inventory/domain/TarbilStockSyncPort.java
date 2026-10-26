package com.vetos.modules.inventory.domain;

import java.math.BigDecimal;
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

    /**
     * Kapali adeti (ve biliniyorsa acik miktari) TARBIL'deki degere getirir (fark kadar IN/OUT, TARBIL_SYNC), TARBIL
     * baglantisini yazar; birim elle ayarlanmadiysa takdim seklinden tahmin eder (spec 2026-10-24).
     */
    void syncFromTarbil(UUID inventoryItemId, int targetClosed, BigDecimal targetOpenedOrNull, Integer dosesPerPackage,
                        TarbilStockLink link, UUID referenceId, java.time.LocalDate openedExpiryDate);

    default void syncFromTarbil(UUID inventoryItemId, int targetClosed, BigDecimal targetOpenedOrNull, Integer dosesPerPackage,
                                TarbilStockLink link, UUID referenceId) {
        syncFromTarbil(inventoryItemId, targetClosed, targetOpenedOrNull, dosesPerPackage, link, referenceId, null);
    }

    /**
     * Her TARBIL stok yuklemesinde eslesen kalem icin (miktar ve hareket yok): birimi tahmin eder (değerlendirme I4; elle
     * ayarlanmış birime ve bekleyen düşümü olan kaleme dokunmaz) ve açık ürün son kullanma tarihini yazar (spec 2026-10-25).
     */
    void refreshFromSnapshot(UUID inventoryItemId, String tarbilSystem, String presentation, Integer dosesPerPackage,
                             java.time.LocalDate openedExpiryDate);
}
