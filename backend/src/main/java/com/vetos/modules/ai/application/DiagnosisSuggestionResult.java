package com.vetos.modules.ai.application;

import java.util.UUID;

public record DiagnosisSuggestionResult(UUID aiJobId, String suggestionText, boolean modelConnected) {}
