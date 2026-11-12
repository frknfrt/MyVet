package com.vetos.modules.platformadmin.domain.exception;

import com.vetos.platform.exception.DomainException;

public class AnnualPlanUnavailableException extends DomainException {
    public AnnualPlanUnavailableException() {
        super("ANNUAL_PLAN_UNAVAILABLE", "Bu paket için yıllık ödeme şu anda kullanılamıyor.");
    }
}
