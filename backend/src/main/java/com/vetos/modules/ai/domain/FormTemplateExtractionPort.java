package com.vetos.modules.ai.domain;

/** Dijital onay formlari: kagit form fotografindan sablon taslagi (ham JSON) cikarir. */
public interface FormTemplateExtractionPort {
    FormTemplateExtractionDraft extract(OcrImageInput image);
}
