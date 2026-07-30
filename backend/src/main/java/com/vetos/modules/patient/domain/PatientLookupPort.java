package com.vetos.modules.patient.domain;

import java.util.UUID;

/**
 * Diger moduller (appointment, encounter, billing) hasta bilgisine
 * SADECE bu port uzerinden erisir. PatientRepository'yi ASLA import etmezler.
 */
public interface PatientLookupPort {
    PatientSummary findSummaryById(UUID patientId);
}
