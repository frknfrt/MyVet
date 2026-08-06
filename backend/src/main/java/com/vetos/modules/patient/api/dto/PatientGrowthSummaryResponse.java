package com.vetos.modules.patient.api.dto;

import com.vetos.modules.patient.application.dto.PatientGrowthSummary;

public record PatientGrowthSummaryResponse(int newPatientsThisMonth, int newPatientsLastMonth) {
    public static PatientGrowthSummaryResponse from(PatientGrowthSummary s) {
        return new PatientGrowthSummaryResponse(s.newPatientsThisMonth(), s.newPatientsLastMonth());
    }
}
