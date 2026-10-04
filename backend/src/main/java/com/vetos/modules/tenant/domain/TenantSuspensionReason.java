package com.vetos.modules.tenant.domain;

/**
 * Bir kiracinin neden askiya alindigi -- churn analizi icin (bkz. platform
 * admin "Genel Bakis" panelindeki neden dagilimi). BILLING_OVERDUE,
 * FlagOverdueAndSuspendUseCase'in OTOMATIK olarak koydugu tek deger; digerleri
 * platform admin'in manuel askiya alma sirasinda secmesi gerekir.
 */
public enum TenantSuspensionReason {
    BILLING_OVERDUE,
    PRICE,
    COMPETITOR,
    NOT_USING,
    DISSATISFIED,
    CLOSED_BUSINESS,
    OTHER
}
