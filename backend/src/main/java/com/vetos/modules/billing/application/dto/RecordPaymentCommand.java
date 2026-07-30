package com.vetos.modules.billing.application.dto;

import com.vetos.modules.billing.domain.PaymentMethod;

import java.math.BigDecimal;
import java.util.UUID;

public record RecordPaymentCommand(UUID invoiceId, PaymentMethod method, BigDecimal amount, String pspRef) {}
