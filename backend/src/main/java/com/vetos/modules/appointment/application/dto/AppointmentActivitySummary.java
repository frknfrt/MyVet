package com.vetos.modules.appointment.application.dto;

public record AppointmentActivitySummary(
    int currentMonthCount, int previousMonthCount,
    double currentMonthNoShowRate, double previousMonthNoShowRate
) {}
