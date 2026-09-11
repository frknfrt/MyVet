package com.vetos.modules.tenant.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Diger moduller (notification gibi tum kiracilar arasinda dolasan
 * zamanlanmis isler) tenant listesine SADECE bu port uzerinden erisir.
 * TenantRepository'yi ASLA import etmezler.
 */
public interface TenantLookupPort {
    List<UUID> findActiveTenantIds();

    /**
     * WhatsApp/SMS bildirimlerinde gonderen klinigin adini gostermek icin
     * (bkz. NotificationSendExecutor). Varsayilan (default) uygulama bos
     * doner -- bu portu implemente eden baska siniflar (ornegin test
     * sahteleri) bu metodu override etmek ZORUNDA degil.
     */
    default Optional<String> findTenantName(UUID tenantId) {
        return Optional.empty();
    }
}
