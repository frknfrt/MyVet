package com.vetos.modules.integration.tarbil.api.dto;

import com.vetos.modules.integration.tarbil.application.dto.PairingCode;

import java.time.Instant;

public record PairingCodeResponse(String code, Instant expiresAt) {
    public static PairingCodeResponse from(PairingCode c) {
        return new PairingCodeResponse(c.code(), c.expiresAt());
    }
}
