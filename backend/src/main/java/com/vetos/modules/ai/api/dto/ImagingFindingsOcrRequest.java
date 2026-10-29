package com.vetos.modules.ai.api.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
public record ImagingFindingsOcrRequest(@NotNull UUID imagingRecordId, @NotEmpty List<UUID> fileIds) {}
