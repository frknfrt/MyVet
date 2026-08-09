package com.vetos.modules.notification.application.dto;

import com.vetos.modules.notification.domain.TemplateChannel;

import java.time.Instant;
import java.util.UUID;

public record MessageTemplateSummary(
    UUID id, String name, TemplateChannel channel, String category, String body, Instant createdAt, Instant updatedAt
) {}
