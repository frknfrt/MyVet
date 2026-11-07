package com.vetos.modules.ai.domain;

/**
 * Modelin ham JSON yaniti (markdown kod blogu temizlenmis). Ayristirma/temizlik/dogrulama
 * use case'te yapilir. modelConnected=false ise rawJson null'dir.
 */
public record FormTemplateExtractionDraft(String rawJson, boolean modelConnected, String modelVersion) {}
