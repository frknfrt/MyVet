package com.vetos.modules.ai.application;

import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
public record ReadImagingFindingsFromImageResult(UUID aiJobId, String findings, boolean modelConnected) {}
