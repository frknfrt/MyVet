package com.vetos.modules.ai.application;

import java.util.UUID;

public record GenerateTreatmentRecommendationCommand(UUID tenantId, UUID encounterId, UUID requestedByStaffUserId) {}
