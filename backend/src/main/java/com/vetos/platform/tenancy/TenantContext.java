package com.vetos.platform.tenancy;

import java.util.UUID;

/**
 * Istek bazli aktif tenant kimligi. JwtAuthenticationFilter, dogrulanan
 * token'daki tenantId'yi istek basina buraya yazar; use-case katmani
 * kendi repository sorgularini elle tenantId parametresi gectirirken
 * bu degeri buradan okur -- client'in govdede gonderdigi bir tenantId'ye
 * ASLA guvenilmez (api-conventions.md, Multi-Tenancy bolumu).
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID current() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Aktif tenant context yok - kimlik dogrulanmis bir istek disinda cagrildi");
        }
        return tenantId;
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
