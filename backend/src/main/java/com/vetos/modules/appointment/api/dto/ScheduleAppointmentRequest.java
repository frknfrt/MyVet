package com.vetos.modules.appointment.api.dto;

import com.vetos.modules.appointment.domain.AppointmentSource;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public record ScheduleAppointmentRequest(
    @NotNull UUID patientId,
    @NotNull UUID ownerId,
    @NotNull UUID assignedStaffId,
    @NotNull UUID serviceTypeId,
    @NotNull Instant scheduledStart,
    @NotNull Instant scheduledEnd,
    AppointmentSource source,
    String notes
) {}
