package com.vetos.platform.cache;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.transaction.TransactionAwareCacheDecorator;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Canli docker-compose Postgres gerekir (tam uygulama baglami). */
@SpringBootTest
class CacheWiringTest {

    @Autowired private CacheManager cacheManager;
    @Autowired private MeterRegistry meterRegistry;

    private Cache<Object, Object> nativeCache(String name) {
        TransactionAwareCacheDecorator decorator = (TransactionAwareCacheDecorator) cacheManager.getCache(name);
        assertThat(decorator).as(name).isNotNull();
        return ((CaffeineCache) decorator.getTargetCache()).getNativeCache();
    }

    @Test
    void should_wrapCaffeineInTransactionAwareProxy() {
        assertThat(cacheManager).isInstanceOf(TransactionAwareCacheManagerProxy.class);
    }

    @Test
    void should_registerExactlyThreeNamedCaches() {
        assertThat(cacheManager.getCacheNames())
            .containsExactlyInAnyOrder(CacheNames.TENANT_PLAN_CODE, CacheNames.PLAN_BY_CODE, CacheNames.TARBIL_DISEASES);
    }

    @Test
    void should_notCreateCache_when_nameUnknown() {
        assertThat(cacheManager.getCache("yanlisYazilmisAd")).isNull();
    }

    @Test
    void should_applyTtlAndSizePerCache() {
        Cache<Object, Object> tenantPlanCode = nativeCache(CacheNames.TENANT_PLAN_CODE);
        assertThat(tenantPlanCode.policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.MINUTES)).isEqualTo(5);
        assertThat(tenantPlanCode.policy().eviction().orElseThrow().getMaximum()).isEqualTo(10_000);

        Cache<Object, Object> planByCode = nativeCache(CacheNames.PLAN_BY_CODE);
        assertThat(planByCode.policy().expireAfterWrite().orElseThrow().getExpiresAfter(TimeUnit.MINUTES)).isEqualTo(5);
        assertThat(planByCode.policy().eviction().orElseThrow().getMaximum()).isEqualTo(200);

        Cache<Object, Object> tarbil = nativeCache(CacheNames.TARBIL_DISEASES);
        assertThat(tarbil.policy().expireAfterWrite()).isEmpty();
        assertThat(tarbil.policy().eviction().orElseThrow().getMaximum()).isEqualTo(1);
    }

    @Test
    void should_recordStats_and_publishMetrics() {
        assertThat(nativeCache(CacheNames.TENANT_PLAN_CODE).policy().isRecordingStats()).isTrue();
        for (String name : cacheManager.getCacheNames()) {
            assertThat(meterRegistry.find("cache.gets").tag("cache", name).meters()).as(name).isNotEmpty();
        }
    }
}
