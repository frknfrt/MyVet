package com.vetos.platform.staff;

import java.util.UUID;

/**
 * Kiracinin paketine gore izin verilen azami personel (staff_user) sayisi (spec 2026-11-05).
 * BranchLimitPort/UsageQuotaPort ile ayni "kota kapaliysa sinirsiz, abonelik/paket bulunamazsa 0"
 * deseni -- bkz. PlanStaffUserLimitAdapter.
 */
public interface StaffUserLimitPort {

    /** null = sinirsiz. */
    Integer maxStaffUsersFor(UUID tenantId);
}
