package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.application.dto.MessageTemplateSummary;
import com.vetos.modules.notification.domain.TemplateChannel;

import java.time.Instant;
import java.util.UUID;

public record MessageTemplateResponse(
    UUID id, String name, TemplateChannel channel, String category, String body, Instant createdAt, Instant updatedAt
) {
    public static MessageTemplateResponse from(MessageTemplateSummary s) {
        return new MessageTemplateResponse(s.id(), s.name(), s.channel(), s.category(), s.body(), s.createdAt(), s.updatedAt());
    }
}
