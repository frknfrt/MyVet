package com.vetos.modules.integration.tarbil.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tarbil_stock_snapshot_line")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TarbilStockSnapshotLine {

    @Id
    private UUID id;

    @Column(name = "snapshot_id", nullable = false)
    private UUID snapshotId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "product_name", nullable = false)
    private String productName;

    private String presentation;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "opened_quantity")
    private BigDecimal openedQuantity;

    @Column(name = "applied_inventory_item_id")
    private UUID appliedInventoryItemId;

    @Column(name = "applied_at")
    private Instant appliedAt;

    /** id hemen atanir: satirlar kaydedilmeden once de kimlikleriyle secilebilsin (testler ve eslesme). */
    public static TarbilStockSnapshotLine of(UUID snapshotId, UUID tenantId, int lineNo, String productName, String presentation,
                                             String lotNumber, LocalDate expiryDate, int quantity, BigDecimal openedQuantity) {
        TarbilStockSnapshotLine line = new TarbilStockSnapshotLine();
        line.id = UUID.randomUUID();
        line.snapshotId = snapshotId;
        line.tenantId = tenantId;
        line.lineNo = lineNo;
        line.productName = productName;
        line.presentation = presentation;
        line.lotNumber = lotNumber;
        line.expiryDate = expiryDate;
        line.quantity = quantity;
        line.openedQuantity = openedQuantity;
        return line;
    }

    public boolean isApplied() {
        return appliedInventoryItemId != null;
    }

    public void markApplied(UUID inventoryItemId, Instant now) {
        this.appliedInventoryItemId = inventoryItemId;
        this.appliedAt = now;
    }
}
