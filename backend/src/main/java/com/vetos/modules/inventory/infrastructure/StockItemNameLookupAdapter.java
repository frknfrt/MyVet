package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.encounter.domain.StockItemNameLookupPort;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/** Encounter'in alerji kontrolu icin stok kalemi adi; TARBIL urun adi farkliysa o da eklenir (eslesme sansini artirir). */
@Component
@RequiredArgsConstructor
class StockItemNameLookupAdapter implements StockItemNameLookupPort {

    private final InventoryItemRepository items;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findName(UUID inventoryItemId) {
        if (inventoryItemId == null) return Optional.empty();
        return items.findById(inventoryItemId).map(i -> {
            String tarbil = i.getTarbilProductName();
            return tarbil == null || tarbil.isBlank() || tarbil.equalsIgnoreCase(i.getName()) ? i.getName() : i.getName() + " (" + tarbil + ")";
        });
    }
}
