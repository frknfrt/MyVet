package com.vetos.modules.appointment.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record PublicAppointmentRequest(
    @NotNull UUID tenantId,
    @NotNull UUID branchId,
    @NotNull UUID patientId,
    @NotNull UUID ownerId,
    @NotNull UUID serviceTypeId,
    @NotNull Instant scheduledStart,
    @NotNull Instant scheduledEnd,
    String notes
) {}
