package com.vetos.modules.tenant.application.dto;

public record RegisterClinicCommand(
    String tenantName,
    String taxNumber,
    String branchName,
    String adminFullName,
    String adminEmail,
    String adminPassword
) {}
