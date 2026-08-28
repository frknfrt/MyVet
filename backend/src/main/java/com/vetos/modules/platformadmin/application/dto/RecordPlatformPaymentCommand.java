package com.vetos.modules.platformadmin.application.dto;

import com.vetos.modules.platformadmin.domain.PlatformPaymentMethod;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecordPlatformPaymentCommand(
    UUID invoiceId, BigDecimal amount, PlatformPaymentMethod method, LocalDate paidAt, String notes, UUID recordedByAdminId
) {}
