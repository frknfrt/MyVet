package com.vetos.platform.security;

import java.util.List;
import java.util.UUID;

/**
 * JWT claim'lerinden cozulmus, istek suresince gecerli kimlik dogrulama
 * bilgisi. api-conventions.md'deki JWT Claim Yapisi ile birebir eslesir.
 * impersonatedBy: platform admin "kiraci olarak gir" oturumunda admin e-postasi
 * (JWT "imp" claim'i); normal giriste null. Denetim kaydi bunu impersonated_by'a yazar.
 */
public record AuthenticatedStaffUser(
    UUID staffUserId,
    UUID tenantId,
    List<UUID> branchIds,
    String role,
    String impersonatedBy,
    long issuedMillis
) {
    public AuthenticatedStaffUser(UUID staffUserId, UUID tenantId, List<UUID> branchIds, String role, String impersonatedBy) {
        this(staffUserId, tenantId, branchIds, role, impersonatedBy, 0L);
    }

    public AuthenticatedStaffUser(UUID staffUserId, UUID tenantId, List<UUID> branchIds, String role) {
        this(staffUserId, tenantId, branchIds, role, null);
    }
}
