package com.vetos.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inventory_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "sku_barcode")
    private String skuBarcode;

    @Column(nullable = false)
    private String name;

    private String category;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand;

    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "lot_number")
    private String lotNumber;

    @Column(name = "unit_cost")
    private BigDecimal unitCost;

    public static InventoryItem create(
        UUID branchId, String name, String category, String skuBarcode,
        int initialQuantity, int reorderThreshold, LocalDate expiryDate, String lotNumber, BigDecimal unitCost
    ) {
        InventoryItem item = new InventoryItem();
        item.branchId = branchId;
        item.name = name;
        item.category = category;
        item.skuBarcode = skuBarcode;
        item.quantityOnHand = initialQuantity;
        item.reorderThreshold = reorderThreshold;
        item.expiryDate = expiryDate;
        item.lotNumber = lotNumber;
        item.unitCost = unitCost;
        return item;
    }

    public void adjustQuantity(int delta) {
        this.quantityOnHand += delta;
    }

    public boolean isBelowReorderThreshold() {
        return quantityOnHand <= reorderThreshold;
    }

    public void updateDetails(String name, String category, int reorderThreshold, BigDecimal unitCost) {
        this.name = name;
        this.category = category;
        this.reorderThreshold = reorderThreshold;
        this.unitCost = unitCost;
    }
}
