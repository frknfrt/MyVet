package com.vetos.modules.consentform.application.dto;

import java.util.Map;
import java.util.UUID;

public record CreateSignedFormCommand(
    UUID templateId, UUID patientId, UUID encounterId, Map<String, Object> values,
    SignerInput signer, String signaturePngBase64, String clientIp, String userAgent
) {}
