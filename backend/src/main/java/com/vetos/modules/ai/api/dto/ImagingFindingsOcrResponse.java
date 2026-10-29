package com.vetos.modules.ai.api.dto;

import com.vetos.modules.ai.application.ReadImagingFindingsFromImageResult;

import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
public record ImagingFindingsOcrResponse(UUID aiJobId, String findings, boolean modelConnected) {
    public static ImagingFindingsOcrResponse from(ReadImagingFindingsFromImageResult r) {
        return new ImagingFindingsOcrResponse(r.aiJobId(), r.findings(), r.modelConnected());
    }
}
