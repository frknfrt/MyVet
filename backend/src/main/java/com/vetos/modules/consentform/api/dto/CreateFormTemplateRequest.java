package com.vetos.modules.consentform.api.dto;

import com.vetos.modules.consentform.domain.FormTemplateContent;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Baslik content.title degerinden turetilir; istekteki ayri title alani yok sayilir. */
public record CreateFormTemplateRequest(@NotNull FormTemplateContent content, UUID sourceImageId) {}
