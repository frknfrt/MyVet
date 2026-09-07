package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AccuracyFeedback;
import java.util.UUID;

public record RecordAiJobFeedbackCommand(UUID aiJobId, AccuracyFeedback feedback) {}
