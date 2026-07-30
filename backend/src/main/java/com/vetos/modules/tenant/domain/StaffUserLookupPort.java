package com.vetos.modules.tenant.domain;

import java.util.UUID;

/**
 * Diger moduller (appointment, encounter, billing...) personel bilgisine
 * SADECE bu port uzerinden erisir. StaffUserRepository'yi ASLA import etmezler.
 */
public interface StaffUserLookupPort {
    StaffSummary findSummaryById(UUID staffUserId);
}
