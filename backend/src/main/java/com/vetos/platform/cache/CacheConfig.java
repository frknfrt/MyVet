package com.vetos.platform.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * Bellek-ici onbellek (spec 2026-10-18 §2). Kurallar:
 * <ul>
 *   <li>Kiraciya ozel onbellekte {@code key = "#tenantId"} acik yazilir; onbellekli metod TenantContext okumaz.</li>
 *   <li>Onbellekli metodlar ayri bir bean'de, public; sinif kendi metodunu cagirirsa proxy atlanir.</li>
 *   <li>Entity degil, degistirilemez record onbellege konur. Kisisel veri onbellege ALINMAZ.</li>
 *   <li>"Bulunamadi" yazilmaz: {@code unless = "#result == null"}.</li>
 * </ul>
 * TransactionAwareCacheManagerProxy: put/evict, aktif islem varsa commit SONRASI uygulanir; rollback'te
 * onbellege dokunulmaz (CaffeineCacheManager'da setTransactionAware yok, ayni davranis bu sarmalayiciyla).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager caffeine = new CaffeineCacheManager();
        caffeine.setAllowNullValues(false);
        // Bos liste: dinamik onbellek olusturmayi kapatir; yalnizca asagida kaydedilenler var.
        caffeine.setCacheNames(List.of());
        caffeine.registerCustomCache(CacheNames.TENANT_PLAN_CODE,
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).maximumSize(10_000).recordStats().build());
        caffeine.registerCustomCache(CacheNames.PLAN_BY_CODE,
            Caffeine.newBuilder().expireAfterWrite(Duration.ofMinutes(5)).maximumSize(200).recordStats().build());
        caffeine.registerCustomCache(CacheNames.TARBIL_DISEASES,
            Caffeine.newBuilder().maximumSize(1).recordStats().build());
        return new TransactionAwareCacheManagerProxy(caffeine);
    }
}
