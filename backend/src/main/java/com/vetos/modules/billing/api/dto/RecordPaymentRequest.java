package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecordPaymentRequest(@NotNull PaymentMethod method, @NotNull @Positive @jakarta.validation.constraints.Digits(integer = 10, fraction = 2) BigDecimal amount, String pspRef, java.util.UUID requestId) {}
