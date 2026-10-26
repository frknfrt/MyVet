package com.vetos.modules.inventory.infrastructure.persistence;

import com.vetos.modules.inventory.domain.DeductionSettlement;
import com.vetos.modules.inventory.domain.PendingDeductionStatus;
import com.vetos.modules.inventory.domain.PendingStockDeduction;
import com.vetos.modules.inventory.domain.StockReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface PendingStockDeductionJpaRepository extends JpaRepository<PendingStockDeduction, UUID> {
    List<PendingStockDeduction> findByReferenceTypeAndReferenceIdOrderByCreatedAt(StockReferenceType type, UUID referenceId);

    List<PendingStockDeduction> findByStatusOrderByCreatedAt(PendingDeductionStatus status);

    boolean existsByInventoryItemIdAndStatus(UUID inventoryItemId, PendingDeductionStatus status);

    List<PendingStockDeduction> findByInventoryItemIdAndStatusOrderByCreatedAt(UUID inventoryItemId, PendingDeductionStatus status);

    @Modifying(flushAutomatically = true)
    @Query("UPDATE PendingStockDeduction p SET p.status = com.vetos.modules.inventory.domain.PendingDeductionStatus.DEDUCTED,"
        + " p.settledQuantity = :quantity, p.settledSource = :source, p.settledVia = :via, p.settledByStaffId = :staffId, p.settledAt = :now"
        + " WHERE p.id = :id AND p.status = com.vetos.modules.inventory.domain.PendingDeductionStatus.PENDING")
    int claim(@Param("id") UUID id, @Param("quantity") java.math.BigDecimal quantity,
              @Param("source") com.vetos.modules.inventory.domain.StockSource source, @Param("via") DeductionSettlement via,
              @Param("staffId") UUID staffId, @Param("now") Instant now);
}
