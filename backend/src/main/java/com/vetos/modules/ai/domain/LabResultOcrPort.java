package com.vetos.modules.ai.domain;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
public interface LabResultOcrPort {
    LabResultOcrDraft generate(LabResultOcrInput input);
}
