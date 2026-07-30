package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.domain.ConsentType;
import jakarta.validation.constraints.NotNull;

public record RecordConsentRequest(@NotNull ConsentType consentType) {}
