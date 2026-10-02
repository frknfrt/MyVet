package com.vetos.modules.integration.tarbil.application.dto;

import java.util.UUID;

public record ExtensionIdentity(UUID tokenId, UUID tenantId, UUID staffUserId) {}
