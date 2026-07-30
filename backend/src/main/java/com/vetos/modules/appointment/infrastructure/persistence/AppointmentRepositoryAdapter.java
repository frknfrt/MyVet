package com.vetos.modules.appointment.infrastructure.persistence;

import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class AppointmentRepositoryAdapter implements AppointmentRepository {

    private final AppointmentJpaRepository jpaRepository;

    @Override
    public Appointment save(Appointment appointment) { return jpaRepository.save(appointment); }

    @Override
    public Optional<Appointment> findById(UUID id) { return jpaRepository.findById(id); }

    @Override
    public List<Appointment> findByBranchAndDateRange(UUID branchId, Instant rangeStart, Instant rangeEnd) {
        return jpaRepository.findByBranchAndDateRange(branchId, rangeStart, rangeEnd);
    }

    @Override
    public List<Appointment> findByOwnerId(UUID ownerId) { return jpaRepository.findByOwnerId(ownerId); }

    @Override
    public boolean existsOverlapping(UUID assignedStaffId, Instant start, Instant end, UUID excludeAppointmentId) {
        return jpaRepository.existsOverlapping(assignedStaffId, start, end, excludeAppointmentId);
    }
}
