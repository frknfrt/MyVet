package com.vetos.modules.appointment.domain.exception;

import com.vetos.platform.exception.DomainException;

public class InvalidAppointmentIntervalException extends DomainException {
    public InvalidAppointmentIntervalException() {
        super("INVALID_APPOINTMENT_INTERVAL", "Randevu bitişi başlangıçtan sonra olmalıdır");
    }
}
