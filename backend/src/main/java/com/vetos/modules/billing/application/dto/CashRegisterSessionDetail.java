package com.vetos.modules.billing.application.dto;

import com.vetos.modules.billing.domain.CashRegisterStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashRegisterSessionDetail(
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
) {}
