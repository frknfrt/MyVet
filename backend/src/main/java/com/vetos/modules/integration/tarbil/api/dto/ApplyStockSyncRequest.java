package com.vetos.modules.integration.tarbil.api.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record ApplyStockSyncRequest(@NotNull @Size(min = 1, max = 500) List<UUID> lineIds) {}
