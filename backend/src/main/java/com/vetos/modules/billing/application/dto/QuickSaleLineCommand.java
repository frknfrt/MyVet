package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record QuickSaleLineCommand(UUID inventoryItemId, String description, int quantity, BigDecimal unitPrice) {}
