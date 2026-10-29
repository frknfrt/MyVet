package com.vetos.modules.ai.application;

import java.util.List;
import java.util.UUID;

/** Lab/goruntuleme sonuc fotografindan AI ile otomatik doldurma (2026-10-29). */
public record ReadLabResultFromImageCommand(UUID tenantId, UUID labResultId, List<UUID> fileIds, UUID requestedByStaffUserId) {}
