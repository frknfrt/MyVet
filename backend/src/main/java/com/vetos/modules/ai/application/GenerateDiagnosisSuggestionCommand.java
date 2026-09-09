package com.vetos.modules.ai.application;

import java.util.UUID;

public record GenerateDiagnosisSuggestionCommand(UUID tenantId, UUID encounterId, UUID requestedByStaffUserId) {}
