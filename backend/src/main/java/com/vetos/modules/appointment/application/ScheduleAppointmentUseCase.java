package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.application.dto.ScheduleAppointmentCommand;
import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.event.AppointmentScheduledEvent;
import com.vetos.modules.appointment.domain.exception.AppointmentSlotConflictException;
import com.vetos.platform.event.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleAppointmentUseCase {

    private final AppointmentRepository appointmentRepository;
    private final NoShowRiskEstimator noShowRiskEstimator;
    private final DomainEventPublisher eventPublisher;

    @Transactional
    public UUID execute(ScheduleAppointmentCommand command) {
        // WIDGET kaynakli talepler henuz bir hekime atanmadan REQUESTED olarak
        // dusuyor (@docs/implementation-plan.md Modul 8) -- atanmamis randevu
        // icin cakisma kontrolu anlamsiz, atlanir.
        if (command.assignedStaffId() != null) {
            boolean conflict = appointmentRepository.existsOverlapping(
                command.assignedStaffId(), command.scheduledStart(), command.scheduledEnd(), null
            );
            if (conflict) {
                throw new AppointmentSlotConflictException(command.assignedStaffId());
            }
        }

        Appointment appointment = Appointment.schedule(
            command.tenantId(), command.branchId(), command.patientId(), command.ownerId(),
            command.assignedStaffId(), command.serviceTypeId(), command.scheduledStart(), command.scheduledEnd(),
            command.source(), command.notes()
        );
        appointment.assignNoShowRiskScore(noShowRiskEstimator.estimateFor(command.ownerId()));
        appointmentRepository.save(appointment);

        eventPublisher.publish(new AppointmentScheduledEvent(
            appointment.getId(), appointment.getPatientId(), appointment.getAssignedStaffId()
        ));
        return appointment.getId();
    }
}
