package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.application.dto.InventoryItemDetail;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListInventoryItemsUseCase {

    private final InventoryItemRepository inventoryItemRepository;

    @Transactional(readOnly = true)
    public List<InventoryItemDetail> execute(UUID branchId) {
        return inventoryItemRepository.findByBranchId(branchId).stream()
            .map(item -> new InventoryItemDetail(
                item.getId(), item.getName(), item.getCategory(), item.getSkuBarcode(), item.getQuantityOnHand(),
                item.getReorderThreshold(), item.isBelowReorderThreshold(), item.getExpiryDate(), item.getLotNumber(), item.getUnitCost()
            ))
            .toList();
    }
}
