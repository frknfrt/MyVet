package com.vetos.modules.consentform.api.dto;

import com.vetos.modules.consentform.domain.SignerType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;
import java.util.UUID;

public record CreateSignedFormRequest(
    @NotNull UUID templateId,
    @NotNull UUID patientId,
    UUID encounterId,
    Map<String, Object> values,
    @NotNull @Valid SignerRequest signer,
    @NotBlank String signaturePngBase64
) {
    public record SignerRequest(@NotNull SignerType type, String name, String nationalId, String phone) {}
}
