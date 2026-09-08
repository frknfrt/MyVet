package com.vetos.modules.encounter.domain;

import java.util.UUID;

/**
 * Diger moduller (billing, inventory) muayene bilgisine SADECE bu port
 * uzerinden erisir. EncounterRepository'yi ASLA import etmezler.
 */
public interface EncounterLookupPort {
    EncounterSummary findSummaryById(UUID encounterId);
    EncounterClinicalContext findClinicalContext(UUID encounterId, int historyLimit);
}
