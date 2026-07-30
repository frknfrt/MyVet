package com.vetos.platform.security;

import java.util.List;
import java.util.UUID;

/**
 * JWT claim'lerinden cozulmus, istek suresince gecerli kimlik dogrulama
 * bilgisi. api-conventions.md'deki JWT Claim Yapisi ile birebir eslesir.
 */
public record AuthenticatedStaffUser(
    UUID staffUserId,
    UUID tenantId,
    List<UUID> branchIds,
    String role
) {
}
