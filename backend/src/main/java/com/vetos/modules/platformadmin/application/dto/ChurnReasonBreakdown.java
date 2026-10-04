package com.vetos.modules.platformadmin.application.dto;

/** reason, TenantSuspensionReason.name() degeri veya henuz neden kaydedilmeden (migrasyon oncesi) askiya alinmis kiracilar icin "UNKNOWN"dir. */
public record ChurnReasonBreakdown(String reason, int count) {}
