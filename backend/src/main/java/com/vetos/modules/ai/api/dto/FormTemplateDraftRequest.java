package com.vetos.modules.ai.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/** Dijital onay formlari: kaynak gorselden sablon taslagi istegi. */
public record FormTemplateDraftRequest(@NotNull UUID sourceImageId) {}
