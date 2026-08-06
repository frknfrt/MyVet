package com.vetos.modules.appointment.domain;

import java.time.Instant;
import java.util.UUID;

public record AppointmentReminderCandidate(UUID appointmentId, UUID ownerId, UUID patientId, Instant scheduledStart) {}
