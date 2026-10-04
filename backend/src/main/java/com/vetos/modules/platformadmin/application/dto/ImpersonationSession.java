package com.vetos.modules.platformadmin.application.dto;

import java.util.UUID;

public record ImpersonationSession(String token, UUID staffUserId, UUID tenantId, UUID branchId, String fullName, String role) {}
