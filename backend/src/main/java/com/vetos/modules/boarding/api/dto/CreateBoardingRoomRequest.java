package com.vetos.modules.boarding.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public record CreateBoardingRoomRequest(
    @NotBlank String groupName,
    @NotBlank String name,
    @Positive int capacity,
    @PositiveOrZero BigDecimal dailyRate,
    String notes
) {}
