package com.vetos.modules.inventory.domain;

import java.util.Optional;
import java.util.UUID;

/** integration/tarbil icin: asinin stok kaleminin TARBIL'deki urun adi (yoksa kalem adi). */
public interface InventoryItemLookupPort {
    Optional<String> findTarbilProductName(UUID inventoryItemId);

    /** Kalemin acik/parca miktari (spec 2026-10-24); kalem yoksa bos. */
    Optional<java.math.BigDecimal> findOpenedQuantity(UUID inventoryItemId);

    /**
     * Asi belgesinde stok penceresinin listesi (ATS kilavuzu 9.2.4.4: daha once acilmis urun Acik/Parca Stok): flakonla kullanilan
     * kalemde en az 1 acik flakon varsa "OPEN", aksi (birim ADET dahil) "WHOLE" (spec 2026-10-25 §4.3/7). Kalem yoksa bos.
     */
    Optional<String> findVaccineStockSource(UUID inventoryItemId);
}
