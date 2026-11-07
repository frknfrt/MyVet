package com.vetos.modules.consentform.application.dto;

import com.vetos.modules.consentform.domain.SignedFormStatus;
import com.vetos.modules.consentform.domain.SignerType;

import java.time.Instant;
import java.util.UUID;

public record SignedFormSummary(
    UUID id, String templateTitle, Instant signedAt, String signerName, SignerType signerType, SignedFormStatus status
) {}
