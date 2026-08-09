package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.dto.BranchWorkingHoursEntry;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record BranchWorkingHoursResponse(DayOfWeek dayOfWeek, boolean closed, LocalTime opensAt, LocalTime closesAt) {
    public static BranchWorkingHoursResponse from(BranchWorkingHoursEntry entry) {
        return new BranchWorkingHoursResponse(entry.dayOfWeek(), entry.closed(), entry.opensAt(), entry.closesAt());
    }
}
