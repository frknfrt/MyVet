package com.vetos.modules.inventory.infrastructure.persistence;

import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.PendingStockDeduction;
import com.vetos.modules.inventory.domain.StockReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface PendingStockDeductionJpaRepository extends JpaRepository<PendingStockDeduction, UUID> {
    List<PendingStockDeduction> findByReferenceTypeAndReferenceIdOrderByCreatedAt(StockReferenceType type, UUID referenceId);

    List<PendingStockDeduction> findByStatusOrderByCreatedAt(PendingDeductionStatus status);
}
