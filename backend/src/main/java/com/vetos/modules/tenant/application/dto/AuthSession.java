package com.vetos.modules.tenant.application.dto;

import com.vetos.modules.tenant.domain.StaffRole;

import java.util.UUID;

/**
 * Login ve RegisterClinic use-case'lerinin ortak ciktisi: uretilmis JWT +
 * frontend'in oturumu gostermek icin ihtiyac duydugu ozet bilgiler.
 */
public record AuthSession(
    String token,
    UUID staffUserId,
    UUID tenantId,
    UUID branchId,
    String fullName,
    StaffRole role
) {}
