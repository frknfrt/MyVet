package com.vetos.modules.tenant.domain.event;

import java.util.UUID;

public record ClinicRegisteredEvent(UUID tenantId, UUID branchId, UUID adminStaffUserId) {}
