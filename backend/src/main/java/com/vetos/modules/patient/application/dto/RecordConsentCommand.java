package com.vetos.modules.patient.application.dto;

import com.vetos.modules.patient.domain.ConsentType;

import java.util.UUID;

public record RecordConsentCommand(UUID ownerId, ConsentType consentType, String ipAddress) {}
