package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.CashRegisterSessionDetail;
import com.vetos.modules.billing.domain.CashRegisterStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashRegisterSessionResponse(
    UUID id,
    UUID branchId,
    UUID openedByStaffId,
    BigDecimal openingBalance,
    Instant openedAt,
    UUID closedByStaffId,
    BigDecimal closingBalance,
    Instant closedAt,
    CashRegisterStatus status,
    String notes
) {
    public static CashRegisterSessionResponse from(CashRegisterSessionDetail d) {
        return new CashRegisterSessionResponse(
            d.id(), d.branchId(), d.openedByStaffId(), d.openingBalance(), d.openedAt(),
            d.closedByStaffId(), d.closingBalance(), d.closedAt(), d.status(), d.notes()
        );
    }
}
