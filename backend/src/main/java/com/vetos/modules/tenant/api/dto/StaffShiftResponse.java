package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.dto.StaffShiftEntry;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record StaffShiftResponse(DayOfWeek dayOfWeek, LocalTime startsAt, LocalTime endsAt) {
    public static StaffShiftResponse from(StaffShiftEntry entry) {
        return new StaffShiftResponse(entry.dayOfWeek(), entry.startsAt(), entry.endsAt());
    }
}
