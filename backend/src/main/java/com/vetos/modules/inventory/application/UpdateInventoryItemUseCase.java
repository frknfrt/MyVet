package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.application.dto.UpdateInventoryItemCommand;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateInventoryItemUseCase {

    private final InventoryItemRepository inventoryItemRepository;

    @Transactional
    public void execute(UpdateInventoryItemCommand command) {
        InventoryItem item = inventoryItemRepository.findById(command.itemId())
            .orElseThrow(() -> new InventoryItemNotFoundException(command.itemId()));
        item.updateDetails(command.name(), command.category(), command.reorderThreshold(), command.unitCost());
        inventoryItemRepository.save(item);
    }
}
