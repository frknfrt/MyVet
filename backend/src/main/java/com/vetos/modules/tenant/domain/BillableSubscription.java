package com.vetos.modules.tenant.domain;

import java.time.LocalDate;
import java.util.UUID;

public record BillableSubscription(UUID tenantId, String planCode, LocalDate renewsAt) {}
