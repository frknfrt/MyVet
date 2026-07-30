package com.vetos.modules.appointment.api.dto;

import com.vetos.modules.appointment.application.dto.ServiceTypeSummary;

import java.math.BigDecimal;
import java.util.UUID;

public record ServiceTypeResponse(UUID id, String name, int defaultDurationMin, BigDecimal defaultPrice) {
    public static ServiceTypeResponse from(ServiceTypeSummary summary) {
        return new ServiceTypeResponse(summary.id(), summary.name(), summary.defaultDurationMin(), summary.defaultPrice());
    }
}
