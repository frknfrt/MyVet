package com.vetos.modules.appointment.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateServiceTypeRequest(
    @NotBlank String name,
    @Positive int defaultDurationMin,
    @NotNull @Positive BigDecimal defaultPrice
) {}
