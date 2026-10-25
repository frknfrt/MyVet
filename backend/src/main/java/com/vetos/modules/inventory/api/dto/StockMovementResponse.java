package com.vetos.modules.inventory.api.dto;

import com.vetos.modules.inventory.application.dto.StockMovementSummary;
import com.vetos.modules.inventory.domain.StockMovementType;
import com.vetos.modules.inventory.domain.StockReferenceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record StockMovementResponse(
    UUID id, StockMovementType movementType, int quantity, BigDecimal openedDelta, StockReferenceType referenceType, UUID referenceId, Instant createdAt
) {
    public static StockMovementResponse from(StockMovementSummary s) {
        return new StockMovementResponse(s.id(), s.movementType(), s.quantity(), s.openedDelta(), s.referenceType(), s.referenceId(), s.createdAt());
    }
}
