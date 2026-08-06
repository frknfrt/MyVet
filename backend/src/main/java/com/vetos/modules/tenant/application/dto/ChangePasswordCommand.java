package com.vetos.modules.tenant.application.dto;

import java.util.UUID;

public record ChangePasswordCommand(UUID staffUserId, String currentPassword, String newPassword) {}
