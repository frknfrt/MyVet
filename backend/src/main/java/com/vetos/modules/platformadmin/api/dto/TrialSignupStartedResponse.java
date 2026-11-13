package com.vetos.modules.platformadmin.api.dto;

import java.util.UUID;

public record TrialSignupStartedResponse(UUID requestId, int resendAvailableInSeconds) {}
