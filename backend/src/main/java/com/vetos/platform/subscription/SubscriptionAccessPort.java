package com.vetos.platform.subscription;

import java.util.UUID;

/**
 * Kiracinin abonelik erisim durumu (spec 2026-11-13 S4.3). FeatureAccessPort ile ayni desen:
 * port platform katmaninda, uygulamasi tenant modulunde (platform bir module bagimli olamaz).
 * Deneme dolmuslugu saklanan bir durum degil, her sorguda tarih karsilastirmasidir.
 */
public interface SubscriptionAccessPort {

    SubscriptionAccess accessOf(UUID tenantId);

    /** Abonelik degisince (odeme, admin duzenlemesi) onbellegi temizler. */
    void evict(UUID tenantId);
}
