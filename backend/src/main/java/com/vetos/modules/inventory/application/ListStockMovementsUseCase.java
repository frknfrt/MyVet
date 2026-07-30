package com.vetos.modules.inventory.application;

import com.vetos.modules.inventory.application.dto.StockMovementSummary;
import com.vetos.modules.inventory.domain.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ListStockMovementsUseCase {

    private final StockMovementRepository stockMovementRepository;

    @Transactional(readOnly = true)
    public List<StockMovementSummary> execute(UUID inventoryItemId) {
        return stockMovementRepository.findByInventoryItemId(inventoryItemId).stream()
            .map(m -> new StockMovementSummary(m.getId(), m.getMovementType(), m.getQuantity(), m.getReferenceType(), m.getReferenceId(), m.getCreatedAt()))
            .sorted(Comparator.comparing(StockMovementSummary::createdAt).reversed())
            .toList();
    }
}
