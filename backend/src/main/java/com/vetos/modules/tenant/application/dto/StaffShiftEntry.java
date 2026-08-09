package com.vetos.modules.tenant.application.dto;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record StaffShiftEntry(DayOfWeek dayOfWeek, LocalTime startsAt, LocalTime endsAt) {}
