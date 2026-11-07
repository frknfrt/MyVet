package com.vetos.modules.encounter.domain;

import java.util.Optional;
import java.util.UUID;

/**
 * Stok kaleminin adi (alerji kontrolu icin). Encounter stok modulune bagimli olamaz (stok modulu encounter'a bagli);
 * bu yuzden port burada tanimli, stok modulu uygular.
 */
public interface StockItemNameLookupPort {
    Optional<String> findName(UUID inventoryItemId);
}
