package com.vetos.platform.tenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Hibernate'in @TenantId filtresini besleyen resolver.
 *
 * ONEMLI (tasarim dokumanindan SAPMA, gerekcesi asagida): tasarim dokumani
 * S3, TenantContext bos oldugunda IllegalStateException firlatmayi
 * ongoruyordu ("sessizce tum kiracilari donmek yerine gurultulu
 * basarisizlik"). Hibernate 6.6'da bu UYGULANABILIR DEGIL:
 * SessionFactoryImpl$SessionBuilderImpl constructor'i, hangi entity'ye
 * dokunulacagindan BAGIMSIZ olarak HER Session acilisinda
 * resolveCurrentTenantIdentifier() cagirir; devaminda
 * AbstractSharedSessionContract.setUpMultitenancy(), @TenantId'li en az bir
 * entity varsa null tenant identifier'i HibernateException ile reddeder.
 * Yani firlatmak; /api/v1/auth/login'i (LoginUseCase e-postadan arar, tenant
 * HENUZ BILINMIYOR -- koprulme yapilamaz), tum /api/v1/public/** uclarini,
 * ayri filtre zincirindeki platform-admin uclarini, @Scheduled isleri,
 * @Async NotificationSendExecutor'u ve acilistaki platform-admin
 * bootstrap'ini komple kilitlerdi.
 *
 * Cozum, Hibernate'in kendi mekanizmasi: context bosken ROOT_TENANT_ID
 * sentinel'i donulur ve isRoot() bunu isaretler -- Hibernate o Session'da
 * _tenantId filtresini HIC etkinlestirmez (root/superuser Session). Bu,
 * TenantContext'siz yollarin BUGUNKU davranisini (filtresiz) aynen korur;
 * kimlik-dogrulanmis her istek -- yani ispatlanmis iki acigin da bulundugu
 * yuzey -- otomatik filtreli hale gelir. Root Session'da kayit (persist)
 * da tutarlidir: TenantIdGeneration, entity'ye elle atanmis tenantId'yi
 * oldugu gibi kullanir; factory konvansiyonu (tenantId ilk parametre) bu
 * degeri her zaman garanti eder.
 *
 * Arka plan/platform-admin kodunun DOGRU davranisi yine de TenantContext'i
 * elle kurmaktir (koprulme kurali, tasarim dokumani S5) -- root Session bir
 * emniyet subabidir, izolasyondan muafiyet ruhsati degil.
 */
@Component
public class TenantContextIdentifierResolver implements CurrentTenantIdentifierResolver<UUID> {

    /**
     * "Kiraci yok" sentinel'i. gen_random_uuid() bu degeri hicbir zaman
     * uretmez, dolayisiyla gercek bir kiraciyla cakisamaz.
     */
    public static final UUID ROOT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Override
    public UUID resolveCurrentTenantIdentifier() {
        UUID tenantId = TenantContext.currentOrNull();
        return tenantId != null ? tenantId : ROOT_TENANT_ID;
    }

    @Override
    public boolean isRoot(UUID tenantId) {
        return ROOT_TENANT_ID.equals(tenantId);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
