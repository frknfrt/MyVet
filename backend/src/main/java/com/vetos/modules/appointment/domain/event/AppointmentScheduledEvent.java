package com.vetos.modules.appointment.domain.event;

import java.util.UUID;

public record AppointmentScheduledEvent(UUID appointmentId, UUID patientId, UUID assignedStaffId) {}
