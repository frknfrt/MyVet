package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record OwnerBalance(
    UUID ownerId, String ownerName, String ownerPhone, BigDecimal outstandingBalance,
    boolean smsConsent, boolean whatsappConsent
) {}
