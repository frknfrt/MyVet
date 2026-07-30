package com.vetos.modules.appointment.api.dto;

import com.vetos.modules.appointment.application.dto.AppointmentCalendarItem;
import com.vetos.modules.appointment.domain.AppointmentSource;
import com.vetos.modules.appointment.domain.AppointmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AppointmentResponse(
    UUID id,
    UUID patientId,
    String patientName,
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
) {
    public static AppointmentResponse from(AppointmentCalendarItem item) {
        return new AppointmentResponse(
            item.id(), item.patientId(), item.patientName(), item.ownerId(), item.ownerName(),
            item.assignedStaffId(), item.staffName(), item.serviceTypeId(), item.serviceName(),
            item.scheduledStart(), item.scheduledEnd(), item.status(), item.noShowRiskScore(), item.source(), item.notes()
        );
    }
}
