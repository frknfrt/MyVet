package com.vetos.modules.tenant.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * DIKKAT: normal *LookupPort deseninden BILINCLI bir sapma -- diger
 * LookupPort'lar (BranchLookupPort, StaffUserLookupPort, TenantLookupPort)
 * sadece okuma sunar. Bu port ise platform admin modulunun herhangi bir
 * kiraciyi goruntuleyip DEGISTIREBILMESI ve YENI bir kiraci YARATABILMESI
 * icin yazma da icerir. Sadece modules.platformadmin bu portu kullanir
 * (architecture.md'ye not dusulmustur). Diger hicbir modul bu portu
 * import ETMEMELIDIR.
 */
public interface TenantAdminPort {
    List<TenantAdminOverview> listAll();
    TenantAdminOverview getOverview(UUID tenantId);
    void updateSubscription(UUID tenantId, String planCode, BillingStatus billingStatus, LocalDate renewsAt);
    void suspend(UUID tenantId);
    void activate(UUID tenantId);

    /** planCode != TRIAL ve renewsAt <= date olan tum abonelikler -- platform faturalama scheduler'i icin. */
    List<BillableSubscription> listSubscriptionsDueOnOrBefore(LocalDate date);
    void advanceRenewal(UUID tenantId, LocalDate newRenewsAt);
    void updateBillingStatus(UUID tenantId, BillingStatus billingStatus);
    Optional<String> findBillingContactEmail(UUID tenantId);
    Optional<String> findBillingContactPhone(UUID tenantId);

    /**
     * Yeni bir kiraci + ilk sube + TRIAL abonelik + ADMIN rolunde ilk personeli
     * tek islemde olusturur -- platform admin panelinden tetiklenir (self-servis
     * kayit kaldirildi). AuthSession/JWT URETMEZ; platform admin baskasinin
     * klinigini olusturuyor, kendi adina giris yapmiyor.
     */
    UUID createTenant(
        String tenantName, String taxNumber, String branchName,
        String adminFullName, String adminEmail, String adminPassword
    );
}
