package com.vetos.modules.appointment.domain.exception;

import com.vetos.platform.exception.DomainException;
import java.util.UUID;

public class AppointmentSlotConflictException extends DomainException {
    public AppointmentSlotConflictException(UUID staffId) {
        super("APPOINTMENT_SLOT_CONFLICT", "Bu hekimin secilen zaman araliginda baska bir randevusu var: " + staffId);
    }
}
