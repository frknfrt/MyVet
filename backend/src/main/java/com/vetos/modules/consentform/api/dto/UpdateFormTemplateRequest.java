package com.vetos.modules.consentform.api.dto;

import com.vetos.modules.consentform.domain.FormTemplateContent;
import jakarta.validation.constraints.NotNull;

public record UpdateFormTemplateRequest(@NotNull FormTemplateContent content) {}
