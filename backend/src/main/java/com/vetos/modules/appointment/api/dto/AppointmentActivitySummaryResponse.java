package com.vetos.modules.appointment.api.dto;

import com.vetos.modules.appointment.application.dto.AppointmentActivitySummary;

public record AppointmentActivitySummaryResponse(
    int currentMonthCount, int previousMonthCount,
    double currentMonthNoShowRate, double previousMonthNoShowRate
) {
    public static AppointmentActivitySummaryResponse from(AppointmentActivitySummary s) {
        return new AppointmentActivitySummaryResponse(
            s.currentMonthCount(), s.previousMonthCount(), s.currentMonthNoShowRate(), s.previousMonthNoShowRate()
        );
    }
}
