package com.vetos.modules.tenant.domain;

import java.time.LocalDate;

public enum BillingPeriod {
    MONTHLY(1), ANNUAL(12);

    private final int months;

    BillingPeriod(int months) { this.months = months; }

    public LocalDate endDate(LocalDate start) { return start.plusMonths(months); }
}
