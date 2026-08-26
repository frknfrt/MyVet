package com.vetos.modules.tenant.application.dto;

import com.vetos.modules.tenant.domain.StaffRole;

public record StaffInvitePublicSummary(String email, String fullName, StaffRole role, String tenantName) {}
