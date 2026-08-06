package com.vetos.modules.boarding.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateBoardingStayRequest(
    @NotNull UUID roomId,
    @NotNull UUID patientId,
    @NotNull LocalDate checkInDate,
    LocalDate expectedCheckOutDate,
    String notes
) {}
