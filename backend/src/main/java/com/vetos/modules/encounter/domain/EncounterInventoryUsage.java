package com.vetos.modules.encounter.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Muayene sirasinda kullanilan stok kalemi (malzeme/ilac). inventory
 * modulunun "InventoryItem" kavramini bilmez, sadece bir UUID referansi
 * tasir -- stoktan dusum EncounterFinalizedEvent uzerinden inventory
 * modulunde yapilir (@docs/architecture.md Bolum 4).
 */
@Entity
@Table(name = "encounter_inventory_usage")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EncounterInventoryUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Column(nullable = false)
    private int quantity;

    public static EncounterInventoryUsage record(UUID encounterId, UUID inventoryItemId, int quantity) {
        EncounterInventoryUsage usage = new EncounterInventoryUsage();
        usage.encounterId = encounterId;
        usage.inventoryItemId = inventoryItemId;
        usage.quantity = quantity;
        return usage;
    }
}
