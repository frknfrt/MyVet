package com.vetos.modules.patient.application.dto;

import java.util.UUID;

public record OwnerSearchResult(UUID id, String fullName, String phone) {}
