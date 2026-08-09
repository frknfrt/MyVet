package com.vetos.modules.billing.application.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record StaffPerformanceLine(
    UUID staffUserId, String staffName, long invoiceCount, BigDecimal totalRevenue, BigDecimal avgInvoiceAmount
) {}
