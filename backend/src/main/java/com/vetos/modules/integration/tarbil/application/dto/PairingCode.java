package com.vetos.modules.integration.tarbil.application.dto;

import java.time.Instant;

public record PairingCode(String code, Instant expiresAt) {}
