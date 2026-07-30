package com.vetos.modules.tenant.domain;

import java.util.UUID;

public record StaffSummary(UUID id, String fullName, StaffRole role, UUID branchId) {}
