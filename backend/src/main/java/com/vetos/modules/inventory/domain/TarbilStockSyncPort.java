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
                        TarbilStockLink link, UUID referenceId);

    /**
     * Yalnız birimi tahmin eder (miktar ve hareket yok; değerlendirme I4). Elle ayarlanmış birime ve bekleyen düşümü olan
     * kaleme dokunmaz: bekleyen miktar birimle okunur.
     */
    void refreshUnitGuess(UUID inventoryItemId, String tarbilSystem, String presentation, Integer dosesPerPackage);
}
