package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.application.dto.CreateInventoryItemCommand;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateInventoryItemUseCase {

    private final InventoryItemRepository inventoryItemRepository;

    @Transactional
    public UUID execute(CreateInventoryItemCommand command) {
        InventoryItem item = InventoryItem.create(
            command.branchId(), command.name(), command.category(), command.skuBarcode(),
            command.initialQuantity(), command.reorderThreshold(), command.expiryDate(),
            command.lotNumber(), command.unitCost()
        );
        return inventoryItemRepository.save(item).getId();
    }
}
