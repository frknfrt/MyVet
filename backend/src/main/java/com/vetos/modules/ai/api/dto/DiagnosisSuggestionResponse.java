package com.vetos.modules.ai.api.dto;

import com.vetos.modules.ai.application.DiagnosisSuggestionResult;
import java.util.UUID;

public record DiagnosisSuggestionResponse(UUID aiJobId, String suggestionText, boolean modelConnected) {
    public static DiagnosisSuggestionResponse from(DiagnosisSuggestionResult r) {
        return new DiagnosisSuggestionResponse(r.aiJobId(), r.suggestionText(), r.modelConnected());
    }
}
