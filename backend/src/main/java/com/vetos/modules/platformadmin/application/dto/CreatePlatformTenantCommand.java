package com.vetos.modules.platformadmin.application.dto;

public record CreatePlatformTenantCommand(
    String tenantName, String taxNumber, String branchName,
    String adminFullName, String adminEmail, String adminPassword
) {}
