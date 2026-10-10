package com.vetos.modules.inventory.infrastructure.persistence;

import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.PendingStockDeduction;
import com.vetos.modules.inventory.domain.PendingStockDeductionRepository;
import com.vetos.modules.inventory.domain.StockReferenceType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
    public List<PendingStockDeduction> findAllPending() {
        return jpaRepository.findByStatusOrderByCreatedAt(PendingDeductionStatus.PENDING);
    }
}
