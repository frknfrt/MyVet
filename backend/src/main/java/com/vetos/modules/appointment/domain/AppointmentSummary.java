package com.vetos.modules.appointment.domain;

import java.util.UUID;

public record AppointmentSummary(UUID id, UUID patientId, UUID assignedStaffId, UUID serviceTypeId) {}
