package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.domain.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record RecordPaymentRequest(@NotNull PaymentMethod method, @NotNull @Positive BigDecimal amount, String pspRef) {}
