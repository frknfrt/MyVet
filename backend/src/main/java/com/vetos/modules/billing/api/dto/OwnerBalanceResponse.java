package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.OwnerBalance;

import java.math.BigDecimal;
import java.util.UUID;

public record OwnerBalanceResponse(UUID ownerId, String ownerName, String ownerPhone, BigDecimal outstandingBalance) {
    public static OwnerBalanceResponse from(OwnerBalance b) {
        return new OwnerBalanceResponse(b.ownerId(), b.ownerName(), b.ownerPhone(), b.outstandingBalance());
    }
}
