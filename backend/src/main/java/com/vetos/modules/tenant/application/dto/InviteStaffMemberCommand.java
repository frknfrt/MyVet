package com.vetos.modules.tenant.application.dto;

import com.vetos.modules.tenant.domain.StaffRole;

import java.util.UUID;

public record InviteStaffMemberCommand(
    UUID tenantId, UUID branchId, String email, String fullName, StaffRole role, UUID invitedByStaffUserId
) {}
