package com.vetos.modules.ai.api.dto;

import com.vetos.modules.ai.application.ReadLabResultFromImageResult;
import com.vetos.modules.lab.domain.LabValueFlag;

import java.util.List;
import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
public record LabResultOcrResponse(UUID aiJobId, List<Item> items, String draftSummary, boolean modelConnected) {

    public record Item(String parameterName, String value, String unit, String referenceRange, LabValueFlag flag) {}

    public static LabResultOcrResponse from(ReadLabResultFromImageResult r) {
        return new LabResultOcrResponse(
            r.aiJobId(),
            r.items().stream().map(i -> new Item(i.parameterName(), i.value(), i.unit(), i.referenceRange(), i.flag())).toList(),
            r.draftSummary(),
            r.modelConnected()
        );
    }
}
