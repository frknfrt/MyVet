package com.vetos.modules.lab.application.dto;

import java.util.UUID;

public record RequestLabResultCommand(UUID tenantId, UUID patientId, UUID orderingStaffId, String testName, String notes) {}
