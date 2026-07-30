package com.vetos.modules.inventory.application.dto;

import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;

import java.time.Instant;
import java.util.UUID;

public record StockMovementSummary(
    UUID id, StockMovementType movementType, int quantity, StockReferenceType referenceType, UUID referenceId, Instant createdAt
) {}
