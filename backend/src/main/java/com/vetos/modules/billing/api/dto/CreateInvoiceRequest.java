package com.vetos.modules.billing.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateInvoiceRequest(@NotNull UUID ownerId) {}
