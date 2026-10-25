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

    /** TARBIL asi sayfasindaki "Parca Stok Ara" tablosundan geldi (2026-10-21); Vetly ekraninda etiket. */
    @Column(name = "part_stock", nullable = false)
    private boolean partStock;

    /** Asi butun stok: kutudaki flakon (TARBIL "Uretilen Doz-Kucuk Hayvan", spec 2026-10-24); yoksa null. */
    @Column(name = "doses_per_package")
    private Integer dosesPerPackage;

    @Column(name = "applied_inventory_item_id")
    private UUID appliedInventoryItemId;

    @Column(name = "applied_at")
    private Instant appliedAt;

    /** id hemen atanir: satirlar kaydedilmeden once de kimlikleriyle secilebilsin (testler ve eslesme). */
    public static TarbilStockSnapshotLine of(UUID snapshotId, UUID tenantId, int lineNo, String productName, String presentation,
                                             String lotNumber, LocalDate expiryDate, int quantity, BigDecimal openedQuantity) {
        return of(snapshotId, tenantId, lineNo, productName, presentation, lotNumber, expiryDate, quantity, openedQuantity, false);
    }

    public static TarbilStockSnapshotLine of(UUID snapshotId, UUID tenantId, int lineNo, String productName, String presentation,
                                             String lotNumber, LocalDate expiryDate, int quantity, BigDecimal openedQuantity,
                                             boolean partStock) {
        return of(snapshotId, tenantId, lineNo, productName, presentation, lotNumber, expiryDate, quantity, openedQuantity, partStock, null);
    }

    public static TarbilStockSnapshotLine of(UUID snapshotId, UUID tenantId, int lineNo, String productName, String presentation,
                                             String lotNumber, LocalDate expiryDate, int quantity, BigDecimal openedQuantity,
                                             boolean partStock, Integer dosesPerPackage) {
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
        line.partStock = partStock;
        line.dosesPerPackage = dosesPerPackage;
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
