package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.domain.TemplateChannel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MessageTemplateRequest(
    @NotBlank String name,
    @NotNull TemplateChannel channel,
    @NotBlank String category,
    @NotBlank String body
) {}
