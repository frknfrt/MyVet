package com.vetos.modules.tenant.api.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record SetBranchWorkingHoursRequest(@NotNull List<DayEntry> days) {
    public record DayEntry(@NotNull DayOfWeek dayOfWeek, boolean closed, LocalTime opensAt, LocalTime closesAt) {}
}
