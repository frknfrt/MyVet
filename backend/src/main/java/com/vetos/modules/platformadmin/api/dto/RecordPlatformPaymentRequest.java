package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.domain.PlatformPaymentMethod;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RecordPlatformPaymentRequest(
    @NotNull BigDecimal amount, @NotNull PlatformPaymentMethod method, @NotNull LocalDate paidAt, String notes
) {}
