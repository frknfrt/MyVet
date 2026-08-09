package com.vetos.platform.security;

import java.util.UUID;

/**
 * Platform admin JWT'sinden cozulmus kimlik. AuthenticatedStaffUser'dan
 * BILINCLI olarak ayri bir tip -- platform admin hicbir kiracıya ait
 * degildir, tenantId/branchIds alanlari YOKTUR (architecture.md'deki
 * paralel auth yigini karari).
 */
public record AuthenticatedPlatformAdmin(
    UUID platformAdminId,
    String email
) {
}
