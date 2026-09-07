package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiTaskType;
import java.util.UUID;

public record RecordAiJobCommand(
    UUID tenantId, AiTaskType taskType, UUID encounterId,
    String suggestionText, String modelName, String modelVersion, UUID requestedByStaffUserId
) {}
