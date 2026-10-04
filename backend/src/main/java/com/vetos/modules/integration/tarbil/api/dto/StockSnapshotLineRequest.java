package com.vetos.modules.integration.tarbil.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockSnapshotLineRequest(
    @NotBlank @Size(max = 300) String productName,
    @Size(max = 200) String presentation,
    @Size(max = 100) String lotNumber,
    LocalDate expiryDate,
    @Min(0) int quantity,
    BigDecimal openedQuantity
) {}
