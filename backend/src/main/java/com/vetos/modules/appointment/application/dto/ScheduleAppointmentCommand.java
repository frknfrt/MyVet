package com.vetos.modules.appointment.application.dto;

import com.vetos.modules.appointment.domain.AppointmentSource;

import java.time.Instant;
import java.util.UUID;

public record ScheduleAppointmentCommand(
    UUID tenantId,
    UUID branchId,
    UUID patientId,
    UUID ownerId,
    UUID assignedStaffId,
    UUID serviceTypeId,
    Instant scheduledStart,
    Instant scheduledEnd,
    AppointmentSource source,
    String notes
) {}
