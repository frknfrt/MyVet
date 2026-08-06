package com.vetos.modules.ai.api.dto;

import com.vetos.modules.ai.domain.SoapDraft;

public record SoapDraftResponse(String subjective, String objective, String assessment, String plan, boolean modelConnected) {
    public static SoapDraftResponse from(SoapDraft d) {
        return new SoapDraftResponse(d.subjective(), d.objective(), d.assessment(), d.plan(), d.modelConnected());
    }
}
