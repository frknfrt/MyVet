package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.cache.CacheNames;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Paket bilgisinin onbellekli okunmasi (spec 2026-10-18 §3). PlanFeatureAccessAdapter ve
 * PlanUsageQuotaAdapter buradan okur. Temizlik: paket olustur/guncelle/sil -> planByCode tamami;
 * abonelik degisikligi -> o kiracinin tenantPlanCode kaydi (commit sonrasi). Veritabani elle
 * degistirilirse en gec 5 dk'da tazelenir. "Bulunamadi" onbellege yazilmaz.
 * Nadir yaris: admin degisikligi commit olmadan once eski degeri okuyan islem, kendi commit'inde
 * eski degeri geri yazabilir; en gec 5 dk (TTL) icinde tazelenir.
 * Bu sinif kendi metodlarini CAGIRMAMALI (proxy atlanir, onbellek devre disi kalir).
 */
@Component
public class PlanLookup {

    private final TenantAdminPort tenantAdminPort;
    private final PlanRepository planRepository;

    public PlanLookup(TenantAdminPort tenantAdminPort, PlanRepository planRepository) {
        this.tenantAdminPort = tenantAdminPort;
        this.planRepository = planRepository;
    }

    @Cacheable(cacheNames = CacheNames.TENANT_PLAN_CODE, key = "#tenantId", unless = "#result == null")
    public Optional<String> planCodeOf(UUID tenantId) {
        return tenantAdminPort.findPlanCode(tenantId);
    }

    @Cacheable(cacheNames = CacheNames.PLAN_BY_CODE, key = "#code", unless = "#result == null")
    public Optional<PlanSnapshot> planByCode(String code) {
        return planRepository.findByCode(code).map(PlanSnapshot::of);
    }
}
