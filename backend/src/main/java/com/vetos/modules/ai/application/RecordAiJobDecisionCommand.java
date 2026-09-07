package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.DecisionStatus;
import java.util.UUID;

public record RecordAiJobDecisionCommand(
    UUID aiJobId, DecisionStatus status, String appliedContent, UUID decidedByStaffUserId
) {}
