package com.vetos.platform.tenancy;

import java.util.UUID;
import java.util.function.Supplier;

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

    /**
     * @throws IllegalArgumentException tenantId,
     *     {@link TenantContextIdentifierResolver#ROOT_TENANT_ID} sentinel'ine
     *     esitse. O sentinel, Hibernate'e bir Session'in _tenantId filtresini
     *     TAMAMEN devre disi biraktirir (bkz. TenantContextIdentifierResolver);
     *     istek verisinden (bozuk bir JWT claim'i, hatali bir fixture, ileride
     *     bir bug) asla buraya sizip kimlik-dogrulanmis bir istegi sessizce
     *     izolasyonsuz calistirmamasi icin burada reddedilir. Yalnizca
     *     resolver'in kendisi, context bosken bu degeri DONER -- hicbir
     *     cagiran onu set() ile elle ATAYAMAZ.
     */
    public static void set(UUID tenantId) {
        if (TenantContextIdentifierResolver.ROOT_TENANT_ID.equals(tenantId)) {
            throw new IllegalArgumentException(
                "ROOT_TENANT_ID sentinel'i TenantContext.set() ile elle atanamaz -- "
                    + "bu deger yalnizca TenantContextIdentifierResolver'in ic kullanimidir "
                    + "ve istek verisinden asla ulasilamaz olmalidir"
            );
        }
        CURRENT_TENANT.set(tenantId);
    }

    public static UUID current() {
        UUID tenantId = CURRENT_TENANT.get();
        if (tenantId == null) {
            throw new IllegalStateException("Aktif tenant context yok - kimlik dogrulanmis bir istek disinda cagrildi");
        }
        return tenantId;
    }

    /**
     * current() ile ayni degeri doner ama context bossa firlatmak yerine null
     * doner. SADECE "kiraci var mi?" sorusunu sormasi gereken altyapi kodu
     * icin (TenantContextIdentifierResolver, koprulme birim testleri) --
     * use-case katmani her zaman current() kullanmalidir.
     */
    public static UUID currentOrNull() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }

    /**
     * Verilen isi TenantContext GECICI OLARAK BOSALTILMIS halde (root
     * Session, _tenantId filtresi kapali) calistirir, sonra cagirandan
     * ONCEKI ambient degeri aynen geri yukler (varsa set(previous), yoksa
     * clear()) -- TenantScopedTestSupport.inRootSession() ile AYNI
     * capture/clear/finally-restore deseni, burada uretim kodu icin.
     *
     * Kullanim alani: staff_users.email gibi GLOBAL (tum kiracilarda
     * essiz) bir kisitin, halihazirda bir kiracinin context'inde calisan
     * authenticated bir istek icinde kontrol edilmesi gerektigi durumlar
     * -- @TenantId'li StaffUser uzerindeki existsByEmail sorgusu, cagiranin
     * KENDI context'inde calisirsa sessizce o kiraciyla filtrelenir ve
     * baska kiracidaki cakisan bir e-postayi KACIRIR (bkz.
     * CreateStaffUserUseCase, InviteStaffMemberUseCase).
     */
    public static <T> T callInRootSession(Supplier<T> work) {
        UUID previous = currentOrNull();
        clear();
        try {
            return work.get();
        } finally {
            if (previous != null) {
                set(previous);
            } else {
                clear();
            }
        }
    }
}
