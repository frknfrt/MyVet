package com.vetos.modules.appointment.domain;

import java.util.UUID;

/**
 * Diger moduller (encounter) randevu bilgisine SADECE bu port uzerinden
 * erisir. AppointmentRepository'yi ASLA import etmezler.
 */
public interface AppointmentLookupPort {
    AppointmentSummary findSummaryById(UUID appointmentId);
}
