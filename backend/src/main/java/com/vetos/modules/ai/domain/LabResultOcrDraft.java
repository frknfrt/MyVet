package com.vetos.modules.ai.domain;

import java.util.List;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
public record LabResultOcrDraft(List<OcrLabItem> items, boolean modelConnected, String modelVersion) {}
