package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.domain.InventoryItem;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.modules.inventory.domain.PendingStockDeductionRepository;
import com.vetos.modules.inventory.domain.StockReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Stok sayfasi: TARBIL dusumu bekleyen kalemler (spec 2026-10-09 S5.5). Kiraci filtresi @TenantId ile. */
@Service
@RequiredArgsConstructor
public class ListPendingDeductionsUseCase {

    private final PendingStockDeductionRepository repository;
    private final InventoryItemRepository items;

    public record PendingDeductionView(UUID id, UUID inventoryItemId, String itemName, String lotNumber, int quantity,
                                       StockReferenceType referenceType, UUID referenceId, Instant createdAt) {}

    @Transactional(readOnly = true)
    public List<PendingDeductionView> execute() {
        return repository.findAllPending().stream().map(p -> {
            Optional<InventoryItem> item = items.findById(p.getInventoryItemId());
            return new PendingDeductionView(p.getId(), p.getInventoryItemId(), item.map(InventoryItem::getName).orElse("—"),
                item.map(InventoryItem::getLotNumber).orElse(null), p.getQuantity(), p.getReferenceType(), p.getReferenceId(), p.getCreatedAt());
        }).toList();
    }
}
