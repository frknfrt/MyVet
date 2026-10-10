package com.vetos.modules.inventory.api.dto;

import com.vetos.modules.inventory.application.ListPendingDeductionsUseCase.PendingDeductionView;

import java.time.Instant;
import java.util.UUID;

public record PendingDeductionResponse(UUID id, UUID inventoryItemId, String itemName, String lotNumber, int quantity,
                                       String referenceType, UUID referenceId, Instant createdAt) {
    public static PendingDeductionResponse from(PendingDeductionView v) {
        return new PendingDeductionResponse(v.id(), v.inventoryItemId(), v.itemName(), v.lotNumber(), v.quantity(),
            v.referenceType().name(), v.referenceId(), v.createdAt());
    }
}
