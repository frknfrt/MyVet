package com.vetos.modules.inventory.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StockMovement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "inventory_item_id", nullable = false)
    private UUID inventoryItemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private StockMovementType movementType;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false)
    private StockReferenceType referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static StockMovement record(
        UUID inventoryItemId, StockMovementType movementType, int quantity, StockReferenceType referenceType, UUID referenceId
    ) {
        StockMovement movement = new StockMovement();
        movement.inventoryItemId = inventoryItemId;
        movement.movementType = movementType;
        movement.quantity = quantity;
        movement.referenceType = referenceType;
        movement.referenceId = referenceId;
        movement.createdAt = Instant.now();
        return movement;
    }
}
