package com.vetos.modules.ai.application;

import java.util.List;
import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
public record ReadImagingFindingsFromImageCommand(UUID tenantId, UUID imagingRecordId, List<UUID> fileIds, UUID requestedByStaffUserId) {}
