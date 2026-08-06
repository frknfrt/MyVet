package com.vetos.modules.appointment.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository {
    Appointment save(Appointment appointment);
    Optional<Appointment> findById(UUID id);
    List<Appointment> findByBranchAndDateRange(UUID branchId, Instant rangeStart, Instant rangeEnd);
    List<Appointment> findByTenantIdAndDateRange(UUID tenantId, Instant rangeStart, Instant rangeEnd);
    List<Appointment> findByOwnerId(UUID ownerId);
    boolean existsOverlapping(UUID assignedStaffId, Instant start, Instant end, UUID excludeAppointmentId);
}
