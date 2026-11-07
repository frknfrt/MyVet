package com.vetos.modules.ai.application;

import com.vetos.modules.consentform.domain.FormTemplateContent;

import java.util.List;
import java.util.UUID;

/** Icerik her zaman taslaktir; warnings kural tabanli temizlik notlaridir (kullaniciya gosterilir). */
public record ExtractFormTemplateFromImageResult(
    UUID aiJobId, boolean modelConnected, FormTemplateContent content, List<String> warnings
) {}
