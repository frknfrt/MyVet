package com.vetos.modules.ai.application;

import com.vetos.modules.lab.domain.LabValueFlag;

import java.util.List;
import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
public record ReadLabResultFromImageResult(UUID aiJobId, List<Item> items, String draftSummary, boolean modelConnected) {
    public record Item(String parameterName, String value, String unit, String referenceRange, LabValueFlag flag) {}
}
