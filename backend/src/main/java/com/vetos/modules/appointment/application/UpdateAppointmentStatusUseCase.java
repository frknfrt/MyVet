package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.domain.Appointment;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.exception.AppointmentNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class UpdateAppointmentStatusUseCase {

    private final AppointmentRepository appointmentRepository;

    public enum Action { CONFIRM, CHECK_IN, START, COMPLETE, CANCEL, NO_SHOW }

    @Transactional
    public void execute(UUID appointmentId, Action action) {
        Appointment appointment = appointmentRepository.findById(appointmentId)
            .orElseThrow(() -> new AppointmentNotFoundException(appointmentId));

        Consumer<Appointment> transition = switch (action) {
            case CONFIRM -> Appointment::confirm;
            case CHECK_IN -> Appointment::checkIn;
            case START -> Appointment::start;
            case COMPLETE -> Appointment::complete;
            case CANCEL -> Appointment::cancel;
            case NO_SHOW -> Appointment::markNoShow;
        };
        transition.accept(appointment);
        appointmentRepository.save(appointment);
    }
}
