package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.GetEnabledFeaturesUseCase;

import java.util.List;

public record EnabledFeaturesResponse(boolean enforcementActive, List<String> enabledFeatures) {

    public static EnabledFeaturesResponse from(GetEnabledFeaturesUseCase.Result result) {
        return new EnabledFeaturesResponse(result.enforcementActive(), result.enabledFeatures());
    }
}
