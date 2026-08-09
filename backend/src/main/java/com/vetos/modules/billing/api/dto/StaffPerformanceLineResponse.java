package com.vetos.modules.billing.api.dto;

import com.vetos.modules.billing.application.dto.StaffPerformanceLine;

import java.math.BigDecimal;
import java.util.UUID;

public record StaffPerformanceLineResponse(
    UUID staffUserId, String staffName, long invoiceCount, BigDecimal totalRevenue, BigDecimal avgInvoiceAmount
) {
    public static StaffPerformanceLineResponse from(StaffPerformanceLine l) {
        return new StaffPerformanceLineResponse(
            l.staffUserId(), l.staffName(), l.invoiceCount(), l.totalRevenue(), l.avgInvoiceAmount()
        );
    }
}
