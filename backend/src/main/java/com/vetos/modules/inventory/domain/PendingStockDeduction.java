package com.vetos.modules.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** TARBIL'de dusum onaylanana kadar bekleyen stok dusumu (spec 2026-10-09 S4). */
@Entity
@Table(name = "pending_stock_deduction")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingStockDeduction {

    @Id
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false)
    private StockReferenceType referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PendingDeductionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "settled_via")
    private DeductionSettlement settledVia;

    @Column(name = "settled_quantity")
    private BigDecimal settledQuantity;

    /** TARBIL'de dusumun yapildigi liste (spec 2026-10-24); bilinmiyorsa null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "settled_source")
    private StockSource settledSource;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "settled_by_staff_id")
    private UUID settledByStaffId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PendingStockDeduction open(UUID tenantId, UUID inventoryItemId, BigDecimal quantity, StockReferenceType type, UUID referenceId) {
        PendingStockDeduction p = new PendingStockDeduction();
        p.id = UUID.randomUUID();
        p.tenantId = tenantId;
        p.inventoryItemId = inventoryItemId;
        p.quantity = quantity;
        p.referenceType = type;
        p.referenceId = referenceId;
        p.status = PendingDeductionStatus.PENDING;
        p.createdAt = Instant.now();
        return p;
    }

    /** @return false ise zaten dusulmus/iptal (idempotent). */
    public boolean settle(BigDecimal settledQuantity, StockSource source, DeductionSettlement via, UUID staffId, Instant now) {
        if (status != PendingDeductionStatus.PENDING) {
            return false;
        }
        this.status = PendingDeductionStatus.DEDUCTED;
        this.settledQuantity = settledQuantity;
        this.settledSource = source;
        this.settledVia = via;
        this.settledByStaffId = staffId;
        this.settledAt = now;
        return true;
    }

    public boolean cancel() {
        if (status != PendingDeductionStatus.PENDING) {
            return false;
        }
        this.status = PendingDeductionStatus.CANCELLED;
        return true;
    }
}
