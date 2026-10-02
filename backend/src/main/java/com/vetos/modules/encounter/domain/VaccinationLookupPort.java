package com.vetos.modules.encounter.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Diger moduller (notification) asi bilgisine SADECE bu port uzerinden
 * erisir. VaccinationRecordRepository'yi ASLA import etmezler -- ayni kural
 * AppointmentLookupPort icin de gecerli.
 */
public interface VaccinationLookupPort {

    /** Bildirim modulunun gunluk asi hatirlatma isi icin -- iptal edilmemis kayitlar. */
    List<VaccinationReminderCandidate> findDueForReminder(UUID tenantId, LocalDate dueDate);

    /** integration/tarbil icin -- kiraci kontrolu cagiranin sorumlulugundadir (tenantId gorunumde). */
    Optional<VaccinationTarbilView> findForTarbil(UUID vaccinationRecordId);
}
