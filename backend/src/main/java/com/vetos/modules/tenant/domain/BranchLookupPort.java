package com.vetos.modules.tenant.domain;

import java.util.List;
import java.util.UUID;

/**
 * Diger moduller (billing, reporting...) sube bilgisine SADECE bu port
 * uzerinden erisir. BranchRepository'yi ASLA import etmezler.
 */
public interface BranchLookupPort {
    BranchSummary findSummaryById(UUID branchId);
    List<BranchSummary> findAllByTenantId(UUID tenantId);
}
