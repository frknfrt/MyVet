package com.vetos.platform.branch;

import java.util.UUID;

/**
 * Kiracinin paketine gore izin verilen azami sube sayisi (spec 2026-11-04).
 * UsageQuotaPort/FeatureAccessPort ile ayni "kota kapaliysa sinirsiz, abonelik/paket
 * bulunamazsa 0" deseni -- bkz. PlanBranchLimitAdapter.
 */
public interface BranchLimitPort {

    /** null = sinirsiz. */
    Integer maxBranchesFor(UUID tenantId);
}
