package com.vetos.modules.tenant.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record SetStaffShiftRequest(@NotNull List<ShiftEntry> shifts) {
    public record ShiftEntry(@NotNull DayOfWeek dayOfWeek, @NotNull LocalTime startsAt, @NotNull LocalTime endsAt) {}
}
