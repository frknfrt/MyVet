package com.vetos.modules.consentform.application.dto;

import com.vetos.modules.consentform.domain.SignerType;

public record SignerInput(SignerType type, String name, String nationalId, String phone) {}
