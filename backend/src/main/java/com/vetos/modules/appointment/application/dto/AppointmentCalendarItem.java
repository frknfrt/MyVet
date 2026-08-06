package com.vetos.modules.appointment.application.dto;

import com.vetos.modules.appointment.domain.AppointmentSource;
import com.vetos.modules.appointment.domain.AppointmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AppointmentCalendarItem(
    UUID id,
    UUID patientId,
    String patientName,
    String patientSpeciesName,
    UUID ownerId,
    String ownerName,
    UUID assignedStaffId,
    String staffName,
    UUID serviceTypeId,
    String serviceName,
    Instant scheduledStart,
    Instant scheduledEnd,
    AppointmentStatus status,
    BigDecimal noShowRiskScore,
    AppointmentSource source,
    String notes
) {}
