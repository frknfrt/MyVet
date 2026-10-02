package com.vetos.modules.integration.tarbil.application.dto;

import com.vetos.modules.integration.tarbil.domain.TarbilMappingKind;

import java.time.Instant;
import java.util.UUID;

public record TarbilMappingSummary(UUID id, TarbilMappingKind kind, String vetlyKey, String tarbilFields, Instant updatedAt) {}
