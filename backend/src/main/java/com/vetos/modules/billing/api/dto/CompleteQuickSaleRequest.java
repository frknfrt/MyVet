package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.domain.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CompleteQuickSaleRequest(
    UUID ownerId, @NotEmpty @Valid List<QuickSaleLineRequest> lines, @NotNull PaymentMethod paymentMethod
) {}
