package com.vetos.modules.encounter.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CheckDrugInteractionsRequest(@NotEmpty List<UUID> drugIds) {}
