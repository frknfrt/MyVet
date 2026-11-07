package com.vetos.modules.ai.api.dto;

import com.vetos.modules.ai.application.ExtractFormTemplateFromImageResult;
import com.vetos.modules.consentform.domain.FormTemplateContent;

import java.util.List;
import java.util.UUID;

/** Dijital onay formlari: AI sablon taslagi (her zaman taslak; hekim/yonetici onaylar). */
public record FormTemplateDraftResponse(UUID aiJobId, boolean modelConnected, FormTemplateContent content, List<String> warnings) {

    public static FormTemplateDraftResponse from(ExtractFormTemplateFromImageResult r) {
        return new FormTemplateDraftResponse(r.aiJobId(), r.modelConnected(), r.content(), r.warnings());
    }
}
