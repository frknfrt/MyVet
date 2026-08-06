package com.vetos.modules.tenant.domain;

import java.util.UUID;

/**
 * Diger moduller (billing, reporting...) sube bilgisine SADECE bu port
 * uzerinden erisir. BranchRepository'yi ASLA import etmezler.
 */
public interface BranchLookupPort {
    BranchSummary findSummaryById(UUID branchId);
}
