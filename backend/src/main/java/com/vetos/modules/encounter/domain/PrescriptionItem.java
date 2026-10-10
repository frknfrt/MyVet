package com.vetos.modules.encounter.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "prescription_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PrescriptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @org.hibernate.annotations.TenantId
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "prescription_id", nullable = false)
    private UUID prescriptionId;

    @Column(name = "drug_id", nullable = false)
    private UUID drugId;

    @Column(nullable = false)
    private String dosage;

    @Column(nullable = false)
    private String frequency;

    @Column(name = "duration_days", nullable = false)
    private int durationDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrugRoute route;

    /** "Klinigin stogundan ver" (spec 2026-10-09 S5.6): stok kalemi ve adet birlikte dolu ya da birlikte bos. */
    @Column(name = "inventory_item_id")
    private UUID inventoryItemId;

    @Column(name = "stock_quantity")
    private Integer stockQuantity;

    public static PrescriptionItem add(UUID tenantId, UUID prescriptionId, UUID drugId, String dosage, String frequency, int durationDays, DrugRoute route) {
        return add(tenantId, prescriptionId, drugId, dosage, frequency, durationDays, route, null, null);
    }

    public static PrescriptionItem add(UUID tenantId, UUID prescriptionId, UUID drugId, String dosage, String frequency, int durationDays,
                                       DrugRoute route, UUID inventoryItemId, Integer stockQuantity) {
        PrescriptionItem item = new PrescriptionItem();
        item.tenantId = tenantId;
        item.prescriptionId = prescriptionId;
        item.drugId = drugId;
        item.dosage = dosage;
        item.frequency = frequency;
        item.durationDays = durationDays;
        item.route = route;
        item.inventoryItemId = inventoryItemId;
        item.stockQuantity = stockQuantity;
        return item;
    }
}
