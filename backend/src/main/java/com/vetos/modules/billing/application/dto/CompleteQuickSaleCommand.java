package com.vetos.modules.billing.application.dto;

import com.vetos.modules.billing.domain.PaymentMethod;

import java.util.List;
import java.util.UUID;

public record CompleteQuickSaleCommand(
    UUID branchId, UUID ownerId, UUID staffUserId, List<QuickSaleLineCommand> lines, PaymentMethod paymentMethod
) {}
