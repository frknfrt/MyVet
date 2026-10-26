package com.vetos.modules.inventory.infrastructure.persistence;

import com.vetos.modules.inventory.domain.DeductionSettlement;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.PendingStockDeduction;
import com.vetos.modules.inventory.domain.PendingStockDeductionRepository;
import com.vetos.modules.inventory.domain.StockReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class PendingStockDeductionRepositoryAdapter implements PendingStockDeductionRepository {

    private final PendingStockDeductionJpaRepository jpaRepository;

    @Override
    public PendingStockDeduction save(PendingStockDeduction deduction) { return jpaRepository.save(deduction); }

    @Override
    public List<PendingStockDeduction> findByReference(StockReferenceType type, UUID referenceId) {
        return jpaRepository.findByReferenceTypeAndReferenceIdOrderByCreatedAt(type, referenceId);
    }

    @Override
    public boolean claimForSettlement(UUID id, java.math.BigDecimal settledQuantity, com.vetos.modules.inventory.domain.StockSource source,
                                      DeductionSettlement via, UUID staffId, Instant now) {
        return jpaRepository.claim(id, settledQuantity, source, via, staffId, now) == 1;
    }

    @Override
    public List<PendingStockDeduction> findAllPending() {
        return jpaRepository.findByStatusOrderByCreatedAt(PendingDeductionStatus.PENDING);
    }

    @Override
    public boolean existsPendingForItem(UUID inventoryItemId) {
        return jpaRepository.existsByInventoryItemIdAndStatus(inventoryItemId, PendingDeductionStatus.PENDING);
    }

    @Override
    public List<PendingStockDeduction> findPendingForItem(UUID inventoryItemId) {
        return jpaRepository.findByInventoryItemIdAndStatusOrderByCreatedAt(inventoryItemId, PendingDeductionStatus.PENDING);
    }
}
