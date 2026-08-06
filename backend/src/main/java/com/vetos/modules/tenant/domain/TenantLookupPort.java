package com.vetos.modules.tenant.domain;

import java.util.List;
import java.util.UUID;

/**
 * Diger moduller (notification gibi tum kiracilar arasinda dolasan
 * zamanlanmis isler) tenant listesine SADECE bu port uzerinden erisir.
 * TenantRepository'yi ASLA import etmezler.
 */
public interface TenantLookupPort {
    List<UUID> findActiveTenantIds();
}
