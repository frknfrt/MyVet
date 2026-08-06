package com.vetos.modules.appointment.infrastructure.persistence;

import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentLookupPort;
import com.vetos.modules.appointment.domain.AppointmentReminderCandidate;
import com.vetos.modules.appointment.domain.AppointmentStatus;
import com.vetos.modules.appointment.domain.AppointmentSummary;
import com.vetos.modules.appointment.domain.exception.AppointmentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class AppointmentLookupAdapter implements AppointmentLookupPort {

    private final AppointmentJpaRepository jpaRepository;

    @Override
    public AppointmentSummary findSummaryById(UUID appointmentId) {
        Appointment a = jpaRepository.findById(appointmentId)
            .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));
        return new AppointmentSummary(a.getId(), a.getPatientId(), a.getAssignedStaffId(), a.getServiceTypeId());
    }

    @Override
    public List<AppointmentReminderCandidate> findConfirmedBetween(UUID tenantId, Instant rangeStart, Instant rangeEnd) {
        return jpaRepository.findByTenantIdAndDateRange(tenantId, rangeStart, rangeEnd).stream()
            .filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED)
            .map(a -> new AppointmentReminderCandidate(a.getId(), a.getOwnerId(), a.getPatientId(), a.getScheduledStart()))
            .toList();
    }
}
