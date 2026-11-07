package com.vetos.modules.consentform.application.dto;

import com.vetos.modules.consentform.domain.FormTemplateContent;
import com.vetos.modules.consentform.domain.SignedFormStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record SignedFormDetail(
    UUID id, String templateTitle, FormTemplateContent content, Map<String, Object> values, SignerInput signer,
    Instant signedAt, String signedByName, UUID patientId, UUID ownerId, UUID encounterId,
    SignedFormStatus status, String voidReason, Instant voidedAt, boolean integrityOk
) {}
