package com.vetos.modules.tenant.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record BranchWorkingHoursEntry(DayOfWeek dayOfWeek, boolean closed, LocalTime opensAt, LocalTime closesAt) {}
