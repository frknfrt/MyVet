package com.vetos.modules.appointment.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AppointmentNotFoundException extends DomainException {
    public AppointmentNotFoundException(UUID id) {
        super("APPOINTMENT_NOT_FOUND", "Randevu bulunamadi: " + id);
    }
}
