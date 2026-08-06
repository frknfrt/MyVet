package com.vetos.modules.appointment.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Diger moduller (encounter, notification) randevu bilgisine SADECE bu port
 * uzerinden erisir. AppointmentRepository'yi ASLA import etmezler.
 */
public interface AppointmentLookupPort {
    AppointmentSummary findSummaryById(UUID appointmentId);

    /** Bildirim modulunun gunluk hatirlatma isi icin -- sadece CONFIRMED randevular. */
    List<AppointmentReminderCandidate> findConfirmedBetween(UUID tenantId, Instant rangeStart, Instant rangeEnd);
}
