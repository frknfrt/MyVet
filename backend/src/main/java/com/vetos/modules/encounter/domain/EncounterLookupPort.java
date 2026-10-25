package com.vetos.modules.encounter.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Diger moduller (billing, inventory) muayene bilgisine SADECE bu port
 * uzerinden erisir. EncounterRepository'yi ASLA import etmezler.
 */
public interface EncounterLookupPort {
    EncounterSummary findSummaryById(UUID encounterId);
    EncounterClinicalContext findClinicalContext(UUID encounterId, int historyLimit);
    /** AI önerisi için: iptal edilmemiş reçeteler, from <= issuedDate <= to, en yeniden eskiye, en fazla limit. */
    List<PrescriptionHistoryEntry> findPrescriptionHistory(UUID patientId, LocalDate from, LocalDate to, int limit);
}
