package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.domain.InventoryItemLookupPort;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class InventoryItemLookupAdapter implements InventoryItemLookupPort {

    private final InventoryItemRepository items;

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findTarbilProductName(UUID inventoryItemId) {
        if (inventoryItemId == null) return Optional.empty();
        return items.findById(inventoryItemId)
            .map(i -> i.getTarbilProductName() != null ? i.getTarbilProductName() : i.getName());
    }
}
