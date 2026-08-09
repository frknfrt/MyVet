package com.vetos.modules.platformadmin.application.dto;

import java.util.UUID;

public record PlatformAdminAuthSession(
    String token,
    UUID platformAdminId,
    String email,
    String fullName
) {}
