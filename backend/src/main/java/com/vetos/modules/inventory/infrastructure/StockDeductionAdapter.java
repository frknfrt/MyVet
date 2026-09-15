package com.vetos.modules.inventory.infrastructure;

import com.vetos.modules.inventory.application.RecordStockMovementUseCase;
import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.StockDeductionPort;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;
import com.vetos.modules.inventory.domain.exception.InsufficientStockException;
import com.vetos.modules.inventory.domain.exception.InventoryItemNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class StockDeductionAdapter implements StockDeductionPort {

    private final InventoryItemRepository inventoryItemRepository;
    private final RecordStockMovementUseCase recordStockMovementUseCase;

    @Override
    public void deductForSale(UUID inventoryItemId, int quantity, UUID invoiceId) {
        InventoryItem item = inventoryItemRepository.findById(inventoryItemId)
            .orElseThrow(() -> new InventoryItemNotFoundException(inventoryItemId));

        if (item.getQuantityOnHand() < quantity) {
            throw new InsufficientStockException(item.getName(), quantity, item.getQuantityOnHand());
        }

        recordStockMovementUseCase.execute(inventoryItemId, StockMovementType.OUT, quantity, StockReferenceType.MANUAL, invoiceId);
    }
}
