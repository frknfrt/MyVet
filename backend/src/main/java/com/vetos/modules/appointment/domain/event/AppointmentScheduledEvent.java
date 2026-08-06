package com.vetos.modules.appointment.domain.event;

import java.time.Instant;
import java.util.UUID;

public record AppointmentScheduledEvent(
    UUID appointmentId, UUID tenantId, UUID patientId, UUID ownerId, UUID assignedStaffId, Instant scheduledStart
) {}
