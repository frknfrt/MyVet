package com.vetos.modules.ai.application;

import java.util.UUID;

/** Dijital onay formlari: kagit form fotografindan sablon taslagi. */
public record ExtractFormTemplateFromImageCommand(UUID tenantId, UUID sourceImageId, UUID requestedByStaffUserId) {}
