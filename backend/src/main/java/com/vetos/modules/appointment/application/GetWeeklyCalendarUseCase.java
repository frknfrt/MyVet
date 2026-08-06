package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.application.dto.AppointmentCalendarItem;
import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.ServiceType;
import com.vetos.modules.appointment.domain.ServiceTypeRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import com.vetos.modules.patient.domain.PatientLookupPort;
import com.vetos.modules.tenant.domain.StaffUserLookupPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GetWeeklyCalendarUseCase {

    private final AppointmentRepository appointmentRepository;
    private final ServiceTypeRepository serviceTypeRepository;
    private final PatientLookupPort patientLookupPort;
    private final OwnerLookupPort ownerLookupPort;
    private final StaffUserLookupPort staffUserLookupPort;

    @Transactional(readOnly = true)
    public java.util.List<AppointmentCalendarItem> execute(UUID branchId, Instant weekStart, Instant weekEnd) {
        return appointmentRepository.findByBranchAndDateRange(branchId, weekStart, weekEnd).stream()
            .map(this::toCalendarItem)
            .toList();
    }

    private AppointmentCalendarItem toCalendarItem(Appointment appointment) {
        var patient = patientLookupPort.findSummaryById(appointment.getPatientId());
        var owner = ownerLookupPort.findSummaryById(appointment.getOwnerId());
        var staff = appointment.getAssignedStaffId() == null
            ? null
            : staffUserLookupPort.findSummaryById(appointment.getAssignedStaffId());
        String serviceName = serviceTypeRepository.findById(appointment.getServiceTypeId())
            .map(ServiceType::getName).orElse(null);

        return new AppointmentCalendarItem(
            appointment.getId(), patient.id(), patient.name(), patient.speciesName(), owner.id(), owner.fullName(),
            appointment.getAssignedStaffId(), staff != null ? staff.fullName() : null,
            appointment.getServiceTypeId(), serviceName,
            appointment.getScheduledStart(), appointment.getScheduledEnd(), appointment.getStatus(),
            appointment.getNoShowRiskScore(), appointment.getSource(), appointment.getNotes()
        );
    }
}
