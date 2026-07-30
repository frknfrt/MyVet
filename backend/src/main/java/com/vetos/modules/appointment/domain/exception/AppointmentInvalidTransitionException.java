package com.vetos.modules.appointment.domain.exception;

import com.vetos.platform.exception.DomainException;
import com.vetos.modules.appointment.domain.AppointmentStatus;

public class AppointmentInvalidTransitionException extends DomainException {
    public AppointmentInvalidTransitionException(AppointmentStatus from, AppointmentStatus to) {
        super("APPOINTMENT_INVALID_TRANSITION", "Randevu " + from + " durumundan " + to + " durumuna gecemez");
    }
}
