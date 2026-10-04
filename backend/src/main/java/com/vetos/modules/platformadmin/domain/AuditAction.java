package com.vetos.modules.platformadmin.domain;

/** AuditLogEntry.action icin sabit degerler -- tek referans noktasi, yazim hatasini engeller. */
public final class AuditAction {

    public static final String TENANT_IMPERSONATED = "TENANT_IMPERSONATED";
    public static final String TENANT_CREATED = "TENANT_CREATED";
    public static final String TENANT_SUSPENDED = "TENANT_SUSPENDED";
    public static final String TENANT_ACTIVATED = "TENANT_ACTIVATED";
    public static final String TENANT_SUBSCRIPTION_UPDATED = "TENANT_SUBSCRIPTION_UPDATED";
    public static final String PLAN_CREATED = "PLAN_CREATED";
    public static final String PLAN_UPDATED = "PLAN_UPDATED";
    public static final String PLAN_DELETED = "PLAN_DELETED";
    public static final String COUPON_CREATED = "COUPON_CREATED";
    public static final String COUPON_ACTIVATED = "COUPON_ACTIVATED";
    public static final String COUPON_DEACTIVATED = "COUPON_DEACTIVATED";
    public static final String INVOICE_VOIDED = "INVOICE_VOIDED";
    public static final String PAYMENT_RECORDED = "PAYMENT_RECORDED";

    private AuditAction() {
    }
}
