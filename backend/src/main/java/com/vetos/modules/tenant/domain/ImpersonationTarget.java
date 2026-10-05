package com.vetos.modules.tenant.domain;

import java.util.UUID;

/**
 * Platform admin bir kiraciyi impersonate ederken (bkz.
 * TenantAdminPort#findImpersonationTarget) "kimin yerine gececegini"
 * tasir -- her zaman o kiracinin ADMIN rolundeki, aktif ilk personeli.
 */
public record ImpersonationTarget(UUID staffUserId, UUID branchId, String fullName, StaffRole role) {}
