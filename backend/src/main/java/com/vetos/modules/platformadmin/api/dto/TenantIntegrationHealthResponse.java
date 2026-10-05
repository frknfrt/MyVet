package com.vetos.modules.platformadmin.api.dto;

import com.vetos.modules.platformadmin.application.dto.TenantIntegrationHealth;

public record TenantIntegrationHealthResponse(long failedEInvoiceCount, long failedTarbilSyncCount) {
    public static TenantIntegrationHealthResponse from(TenantIntegrationHealth h) {
        return new TenantIntegrationHealthResponse(h.failedEInvoiceCount(), h.failedTarbilSyncCount());
    }
}
