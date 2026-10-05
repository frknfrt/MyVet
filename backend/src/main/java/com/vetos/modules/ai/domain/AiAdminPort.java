package com.vetos.modules.ai.domain;

import java.util.List;

/**
 * DIKKAT: TenantAdminPort ile ayni bilincli sapma deseni (bkz. o arayuzun
 * javadoc'u, modules.tenant.domain) -- platform admin'in TUM kiracilarin
 * AI kullanim istatistiklerini tek ekranda gorebilmesi icin. Sadece
 * modules.platformadmin bu portu kullanir. Diger hicbir modul bu portu
 * import ETMEMELIDIR.
 */
public interface AiAdminPort {
    List<AiTenantUsage> listUsageByTenant();
}
