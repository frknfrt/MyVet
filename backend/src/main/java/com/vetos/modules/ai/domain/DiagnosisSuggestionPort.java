package com.vetos.modules.ai.domain;

public interface DiagnosisSuggestionPort {
    DiagnosisSuggestionDraft generate(DiagnosisSuggestionInput input);
}
