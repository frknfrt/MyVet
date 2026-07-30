package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.exception.AppointmentNotFoundException;
import com.vetos.modules.appointment.domain.exception.AppointmentSlotConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Widget'tan gelen, henuz hekim atanmamis (@code assignedStaffId == null)
 * talepleri resepsiyonun bir hekime atamasi icin (Modul 8).
 */
@Service
@RequiredArgsConstructor
public class AssignStaffToAppointmentUseCase {

    private final AppointmentRepository appointmentRepository;

    @Transactional
    public void execute(UUID appointmentId, UUID staffId) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        boolean conflict = appointmentRepository.existsOverlapping(
            staffId, appointment.getScheduledStart(), appointment.getScheduledEnd(), appointment.getId()
        );
        if (conflict) {
            throw new AppointmentSlotConflictException(staffId);
        }

        appointment.assignStaff(staffId);
        appointmentRepository.save(appointment);
    }
}
