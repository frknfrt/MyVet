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
    void suspend(UUID tenantId, TenantSuspensionReason reason, String note);
    void activate(UUID tenantId);

    /** planCode != TRIAL ve renewsAt <= date olan tum abonelikler -- platform faturalama scheduler'i icin. */
    List<BillableSubscription> listSubscriptionsDueOnOrBefore(LocalDate date);

    /**
     * billingStatus = CANCELED ve renewsAt <= date olan, henuz askiya
     * alinmamis abonelikler -- odenmis donem sonu kontrolu icin platform
     * faturalama scheduler'i (SuspendExpiredCancellationsUseCase).
     */
    List<BillableSubscription> listCanceledSubscriptionsDueOnOrBefore(LocalDate date);
    void advanceRenewal(UUID tenantId, LocalDate newRenewsAt);
    void updateBillingStatus(UUID tenantId, BillingStatus billingStatus);
    Optional<String> findBillingContactEmail(UUID tenantId);
    Optional<String> findBillingContactPhone(UUID tenantId);

    /** Kiracinin abonelikteki plan kodu -- plan ozellik kisitlamasi (FeatureAccessPort) icin. */
    Optional<String> findPlanCode(UUID tenantId);

    /**
     * Yeni bir kiraci + ilk sube (adres/sehir dahil, TAM doldurulmus) + TRIAL
     * abonelik + ADMIN rolunde ilk personeli tek islemde olusturur -- platform
     * admin panelinden tetiklenir (self-servis kayit kaldirildi). Adres/sehir
     * bilgisinin olusturma aninda zorunlu olmasi, "/kurulum" sihirbazinin
     * gereksiz hale gelmesini saglar (o sayfa bu yuzden kaldirildi) -- bir
     * kiracinin subesi hicbir zaman eksik bilgiyle olusmaz. AuthSession/JWT
     * URETMEZ; platform admin baskasinin klinigini olusturuyor, kendi adina
     * giris yapmiyor.
     */
    UUID createTenant(
        String tenantName, String taxNumber, String branchName, String address, String city,
        String adminFullName, String adminEmail, String adminPassword
    );

    /**
     * Odeme sonrasi self-servis kayit icin: Tenant + Branch + Subscription
     * (secilen plan, ACTIVE durumda -- TRIAL DEGIL) olusturur ama StaffUser
     * OLUSTURMAZ -- ilk admin, ayri bir StaffInvite kabul ederek kendi
     * hesabini/sifresini olusturur (mevcut invite-accept akisiyla ayni).
     * createTenant(...)'tan farkli olarak burada admin sifresi CAGIRAN
     * TARAFTAN gelmiyor -- odeme yapan ziyaretci henuz hicbir sifre girmedi.
     */
    default TenantSignupResult createTenantForPaidSignup(
        String tenantName, String taxNumber, String branchName, String address, String city,
        String planCode, LocalDate renewsAt
    ) {
        return createTenantForPaidSignup(tenantName, taxNumber, branchName, address, city, planCode, renewsAt, BillingPeriod.MONTHLY);
    }

    TenantSignupResult createTenantForPaidSignup(
        String tenantName, String taxNumber, String branchName, String address, String city,
        String planCode, LocalDate renewsAt, BillingPeriod billingPeriod
    );

    /**
     * Odeme sonrasi self-servis kayit icin: yeni tenant'in ilk admin'i icin
     * bir StaffInvite olusturur ve kaydeder (StaffInviteRepository sadece bu
     * modul icinde kullanilir -- diger moduller bu port araciligiyla erisir).
     */
    /**
     * Vitrinden self-servis deneme kaydi (spec 2026-11-13): createTenant ile ayni adimlar, ama
     * yonetici sifresi ZATEN hash olarak gelir (kayit istegi duz sifreyi saklamaz) ve yoneticinin
     * telefonu ile oda sicil numarasi da yazilir. Vergi no/adres bilinmedigi icin "-" konur.
     */
    UUID createTrialTenant(
        String tenantName, String city, String adminFullName, String adminEmail,
        String adminPasswordHash, String adminPhoneE164, String licenseNumber
    );

    /**
     * Denemeden ucretli pakete gecis (odeme onaylaninca): abonelik secilen pakete ACTIVE olarak
     * gecer, Tenant.status ACTIVE olur.
     */
    void activatePaidPlan(UUID tenantId, String planCode, LocalDate renewsAt, BillingPeriod billingPeriod);

    StaffInvite createAdminInviteForPaidSignup(UUID tenantId, UUID branchId, String email, String fullName);

    /** Odeme oncesi e-posta benzersizligini kontrol etmek icin. */
    boolean isEmailRegistered(String email);

    /**
     * Platform admin'in impersonate edecegi kiracinin ADMIN rolundeki, aktif
     * ilk personeli -- bulunamazsa (hic ADMIN'i yoksa) bos doner, cagiran
     * kullaniciya anlamli bir hata gosterir (bkz. StartImpersonationUseCase).
     */
    Optional<ImpersonationTarget> findImpersonationTarget(UUID tenantId);
}
