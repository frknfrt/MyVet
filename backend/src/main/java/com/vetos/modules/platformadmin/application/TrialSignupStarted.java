package com.vetos.modules.platformadmin.application;

import java.util.UUID;

public record TrialSignupStarted(UUID requestId, int resendAvailableInSeconds) {}
