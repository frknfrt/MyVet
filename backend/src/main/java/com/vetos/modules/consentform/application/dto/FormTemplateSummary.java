package com.vetos.modules.consentform.application.dto;

import com.vetos.modules.consentform.domain.FormTemplateStatus;

import java.time.Instant;
import java.util.UUID;

public record FormTemplateSummary(
    UUID id, String title, FormTemplateStatus status, Instant updatedAt, long signedCount, boolean hasSourceImage
) {}
