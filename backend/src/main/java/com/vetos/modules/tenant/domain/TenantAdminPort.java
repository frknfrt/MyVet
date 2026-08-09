package com.vetos.modules.tenant.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * DIKKAT: normal *LookupPort deseninden BILINCLI bir sapma -- diger
 * LookupPort'lar (BranchLookupPort, StaffUserLookupPort, TenantLookupPort)
 * sadece okuma sunar. Bu port ise platform admin modulunun herhangi bir
 * kiraciyi goruntuleyip DEGISTIREBILMESI icin yazma da icerir. Sadece
 * modules.platformadmin bu portu kullanir (architecture.md'ye not
 * dusulmustur). Diger hicbir modul bu portu import ETMEMELIDIR.
 */
public interface TenantAdminPort {
    List<TenantAdminOverview> listAll();
    TenantAdminOverview getOverview(UUID tenantId);
    void updateSubscription(UUID tenantId, String planCode, BillingStatus billingStatus, LocalDate renewsAt);
    void suspend(UUID tenantId);
    void activate(UUID tenantId);
}
