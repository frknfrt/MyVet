package com.vetos.modules.inventory.domain;

import java.util.Optional;
import java.util.UUID;

/** integration/tarbil icin: asinin stok kaleminin TARBIL'deki urun adi (yoksa kalem adi). */
public interface InventoryItemLookupPort {
    Optional<String> findTarbilProductName(UUID inventoryItemId);
}
