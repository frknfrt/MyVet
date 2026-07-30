package com.vetos.modules.appointment.infrastructure.persistence;

import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentLookupPort;
import com.vetos.modules.appointment.domain.AppointmentSummary;
import com.vetos.modules.appointment.domain.exception.AppointmentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

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
}
