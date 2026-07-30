package com.vetos.modules.patient.application.dto;

import java.util.UUID;

public record RegisterOwnerCommand(
    UUID tenantId,
    String fullName,
    String phone,
    String email,
    String address,
    boolean marketingConsent
) {}
