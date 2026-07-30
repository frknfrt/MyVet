package com.vetos.modules.patient.domain;

import java.util.UUID;

/**
 * Diger moduller (billing, appointment...) sahip bilgisine SADECE bu port
 * uzerinden erisir. OwnerRepository'yi ASLA import etmezler.
 */
public interface OwnerLookupPort {
    OwnerSummary findSummaryById(UUID ownerId);
}
