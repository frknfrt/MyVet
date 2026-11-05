package com.vetos.modules.tenant.domain;

/**
 * Bir kiracinin neden askiya alindigi -- churn analizi icin (bkz. platform
 * admin "Genel Bakis" panelindeki neden dagilimi). Otomatik konan iki deger
 * var: BILLING_OVERDUE (FlagOverdueAndSuspendUseCase) ve SELF_CANCELLED
 * (SuspendExpiredCancellationsUseCase -- klinik kendi istegiyle iptal etti,
 * odedigi donem sonunda otomatik askiya alindi); digerleri platform admin'in
 * manuel askiya alma sirasinda secmesi gerekir.
 */
public enum TenantSuspensionReason {
    BILLING_OVERDUE,
    SELF_CANCELLED,
    PRICE,
    COMPETITOR,
    NOT_USING,
    DISSATISFIED,
    CLOSED_BUSINESS,
    OTHER
}
