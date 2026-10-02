package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.ExtensionProfile;

public record ExtensionProfileResponse(String clinicName, String staffName) {
    public static ExtensionProfileResponse from(ExtensionProfile p) {
        return new ExtensionProfileResponse(p.clinicName(), p.staffName());
    }
}
