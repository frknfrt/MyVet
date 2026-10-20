# Faz 6 — Önbellek ve Özet Sorguları Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Paket bilgisi ve TARBİL hastalık listesi için kiracı-güvenli `@Cacheable` önbellek eklemek. Dört ana sayfa özetini önbelleksiz, veritabanında toplayan ve İstanbul saatine göre bölen sorgulara çevirmek.

**Architecture:**
- `platform.cache` paketi `CaffeineCacheManager`'ı kurar ve `TransactionAwareCacheManagerProxy` ile sarar. Böylece put/evict işlemleri commit sonrasına kalır.
- Önbellek olarak yalnızca üç adlandırılmış önbellek var, dinamik önbellek açılmaz.
- Platformadmin'de `PlanLookup` sınıfı iki adaptörün paket okumasını önbellekten yapar. Önbellek temizliği dört use case'teki `@CacheEvict` ile olur.
- Özet use case'leri `Clock` alır, ay/gün sınırlarını `Europe/Istanbul` ile hesaplar ve repository'deki SUM/COUNT sorgularını kullanır.

**Tech Stack:** Spring Boot 3.5.16 (Spring 6.2.19), `spring-boot-starter-cache`, Caffeine (Boot BOM), JPA/JPQL, JUnit 5 + Mockito + AssertJ, gerçek Postgres (docker, port 5433).

**Spec:** `docs/superpowers/specs/2026-10-18-onbellek-faz6-design.md`

## Global Constraints

- Commit/push YOK. Değişiklikler çalışma ağacında (IntelliJ changelist) kalır. Branch açılmaz, `main` üzerinde çalışılır.
- Testler `backend/` içinde `mvn -q test -Dtest=...` ile çalışır. `@SpringBootTest`, docker-compose Postgres'i (5433) ister: kapalıysa `backend/` içinde `docker compose up -d`.
- Önbellek adları tam olarak şunlar: `tenantPlanCode`, `planByCode`, `tarbilDiseases`.
- Süreler ve sınırlar:
  - `tenantPlanCode`: `expireAfterWrite` 5 dk, `maximumSize` 10 000.
  - `planByCode`: 5 dk, 200.
  - `tarbilDiseases`: süresiz, 1.
- Kural 1: Kiracıya özel önbellekte `key = "#tenantId"` açıkça yazılır. Önbellekli metod içinde `TenantContext` okunmaz.
- Kural 2: Önbellekli metodlar ayrı bir bean'de durur ve yalnızca başka bean'lerden çağrılır. Bu metodlar `public` olmalı (Spring'in annotation tabanlı cache kaynağı yalnızca public metodları işler).
- Kural 3: Entity önbelleğe konmaz; yalnızca değiştirilemez record konur (`Set.copyOf` / `List.copyOf` / `Stream.toList()`).
- Kural 4: Kişisel veri (hasta, sahip, personel, TCKN, telefon, e-posta) önbelleğe alınmaz.
- Kural 5: "Bulunamadı" önbelleğe yazılmaz: `unless = "#result == null"`.
- Paket kararlarının sırası birebir aynı kalır:
  - kısıtlama kapalı → hepsi açık / say ama engelleme;
  - abonelik yok → kapalı / 0 + WARN;
  - TRIAL → açık / yml;
  - paket → işaretler / sütun (`null` = sınırsız);
  - paket satırı yok → kapalı / 0 + WARN.
- Özetlerde saat dilimi: `ZoneId.of("Europe/Istanbul")`. Aralıklar `[başlangıç, bitiş)`. Gelir durumları: `ISSUED`, `PARTIALLY_PAID`, `PAID`. Cevap DTO'ları değişmez, ön yüz değişmez.
- `management.endpoints.web.exposure.include` değiştirilmez (`heapdump` kapalı kalır).
- Yorum dili: mevcut koddaki gibi ASCII Türkçe Javadoc/yorum (ş→s, ı→i vb.), sade.

## Review Focus

1. **Gece yarısı sınırı:** Ayın 1'inde 00:30'da (İstanbul) kesilen fatura bu aya, son günün 23:30'unda kesilen o aya sayılmalı. Bugünkü satışta 00:30 satışı "bugün" sayılmalı. Görev 4 ve 5'in Clock'lu birim testleri ve Görev 4'ün DB testi bunu sabitler.
2. **Commit öncesi okuma:** Abonelik güncelleme işlemi commit olmadan önce aynı işlem içinde yapılan okuma eski değeri görmeli. Rollback olursa önbellek eski değeri korumalı. Görev 2'deki `PlanLookupCacheTest` sabitler.
3. **Yeni kiracı / yeni abonelik:** Önce "abonelik yok" okunur, sonra abonelik eklenir. Temizlik çağrılmasa bile sonraki okuma yeni değeri görmeli. Görev 2 sabitler.
4. **Kiracılar arası sızıntı:** A'nın paket kodu ve özet toplamları B'ye karışmamalı. Görev 2 (önbellek) ve Görev 4–5 (B kiracısının faturası/hastası/randevusu A'nın sayısına girmez) sabitler.
5. **Yanlış yazılmış önbellek adı:** Sessizce yeni, sınırsız bir önbellek açılmamalı. Görev 1'in bağlantı testi `getCache("yok") == null` ile sabitler.

---

### Task 1: Önbellek altyapısı (`platform.cache`)

**Files:**
- Modify: `backend/pom.xml` (bağımlılıklar bölümü, `resilience4j-spring-boot3`'ün yanına)
- Create: `backend/src/main/java/com/vetos/platform/cache/package-info.java`
- Create: `backend/src/main/java/com/vetos/platform/cache/CacheNames.java`
- Create: `backend/src/main/java/com/vetos/platform/cache/CacheConfig.java`
- Test: `backend/src/test/java/com/vetos/platform/cache/CacheWiringTest.java`

**Interfaces:**
- Produces:
  - `com.vetos.platform.cache.CacheNames` sabitleri `TENANT_PLAN_CODE = "tenantPlanCode"`, `PLAN_BY_CODE = "planByCode"`, `TARBIL_DISEASES = "tarbilDiseases"` (public static final String). Sonraki görevler etiketlerde bunları kullanır.
  - `CacheManager` bean'i (tipi `TransactionAwareCacheManagerProxy`).
  - Named interface: `platform::cache`.

- [ ] **Step 1: Bağlantı testini yaz**

```java
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
```

- [ ] **Step 2: Testin derlenmediğini/başarısız olduğunu gör**

Run: `cd backend && mvn -q test -Dtest=CacheWiringTest`
Expected: FAIL. Derleme hatası `cannot find symbol ... CacheNames` (ya da `CacheManager` bean'i yok).

- [ ] **Step 3: Bağımlılığı ekle**

`backend/pom.xml`'de `resilience4j-spring-boot3` bağımlılığının hemen arkasına:

```xml
        <!-- Faz 6: Spring Cache soyutlamasi; Caffeine zaten var (Faz 4). Surum Boot BOM'undan. -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-cache</artifactId>
        </dependency>
```

- [ ] **Step 4: `package-info.java` ve `CacheNames`**

```java
@org.springframework.modulith.NamedInterface("cache")
package com.vetos.platform.cache;
```

```java
package com.vetos.platform.cache;

/**
 * Uygulamadaki onbellek adlarinin TEK listesi (spec 2026-10-18 §2). CacheConfig yalnizca bunlari
 * kurar; listede olmayan bir ad icin onbellek ACILMAZ (yanlis yazilmis ad sessizce sinirsiz
 * onbellek yaratmasin).
 */
public final class CacheNames {

    /** Kiracinin abonelikteki paket kodu. Anahtar: tenantId. */
    public static final String TENANT_PLAN_CODE = "tenantPlanCode";
    /** Paketin sade kopyasi (acik moduller + kotalar). Anahtar: paket kodu; paketler herkese ortak. */
    public static final String PLAN_BY_CODE = "planByCode";
    /** TARBIL hastalik listesi; tum kliniklerde ayni referans tablosu. */
    public static final String TARBIL_DISEASES = "tarbilDiseases";

    private CacheNames() {
    }
}
```

- [ ] **Step 5: `CacheConfig`**

```java
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
```

Not: `setAllowNullValues(false)` + `unless = "#result == null"` birlikte "bulunamadı" sonucunun hiç yazılmamasını garanti eder. Önbellekli metod `null` döndürürse (Optional boşken açılan değer) Spring yazmaya çalışmaz, çünkü `unless` önce değerlendirilir.

- [ ] **Step 6: Testi çalıştır**

Run: `cd backend && mvn -q test -Dtest=CacheWiringTest`
Expected: PASS (5 test).

Not: `should_notCreateCache_when_nameUnknown` başarısız olursa, `setCacheNames(List.of())` çağrısının `registerCustomCache` çağrılarından ÖNCE olduğunu doğrula. Spring 6.2'de `setCacheNames(boş olmayan-null)` dinamik modu kapatır.

- [ ] **Step 7: Modül testleri**

Run: `cd backend && mvn -q test -Dtest=ApplicationModulesTest`
Expected: PASS.

---

### Task 2: Paket bilgisi önbelleği (`PlanLookup`) ve temizlik

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanSnapshot.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanLookup.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanFeatureAccessAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanUsageQuotaAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/application/CreatePlanUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/application/UpdatePlanUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/application/DeletePlanUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/application/UpdateTenantSubscriptionUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/package-info.java` (`allowedDependencies`'e `"platform::cache"`)
- Modify (yalnızca kurucu çağrıları): `backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanFeatureAccessAdapterTest.java`, `.../PlanUsageQuotaAdapterTest.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanLookupCacheTest.java`

**Interfaces:**
- Consumes: `CacheNames.TENANT_PLAN_CODE`, `CacheNames.PLAN_BY_CODE` (Task 1).
- Produces:
  - `public class PlanLookup` (`@Component`), kurucu `PlanLookup(TenantAdminPort, PlanRepository)`.
    - `public Optional<String> planCodeOf(UUID tenantId)`
    - `public Optional<PlanSnapshot> planByCode(String code)`
  - `public record PlanSnapshot(String code, Set<Feature> enabledFeatures, Integer monthlyAiQuota, Integer monthlyMessageQuota)` ve `static PlanSnapshot of(Plan)`.
  - Adaptör kurucuları:
    - `PlanFeatureAccessAdapter(PlanLookup, boolean enforcementEnabled)`
    - `PlanUsageQuotaAdapter(PlanLookup, MonthlyUsageStore, QuotaProperties, boolean)` ve `(…, Clock)`

- [ ] **Step 1: Önbellek davranış testini yaz**

```java
package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.application.DeletePlanUseCase;
import com.vetos.modules.platformadmin.application.RecordAuditLogUseCase;
import com.vetos.modules.platformadmin.application.UpdateTenantSubscriptionUseCase;
import com.vetos.modules.platformadmin.application.dto.UpdateTenantSubscriptionCommand;
import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.tenant.domain.BillingStatus;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Gercek CacheManager + gercek islem yoneticisi; veri kaynaklari sahte. Her test kendi rastgele
 * kiraci kimligini / paket kodunu kullanir (onbellek baglam boyunca paylasilir).
 * Canli docker-compose Postgres gerekir.
 */
@SpringBootTest
class PlanLookupCacheTest {

    @MockitoBean private TenantAdminPort tenantAdminPort;
    @MockitoBean private PlanRepository planRepository;
    @MockitoBean private RecordAuditLogUseCase recordAuditLogUseCase;

    @Autowired private PlanLookup planLookup;
    @Autowired private UpdateTenantSubscriptionUseCase updateTenantSubscriptionUseCase;
    @Autowired private DeletePlanUseCase deletePlanUseCase;
    @Autowired private PlatformTransactionManager transactionManager;

    private static final UUID ADMIN = UUID.randomUUID();

    private void changeSubscription(UUID tenantId, String planCode) {
        updateTenantSubscriptionUseCase.execute(
            new UpdateTenantSubscriptionCommand(tenantId, planCode, BillingStatus.ACTIVE, LocalDate.of(2027, 1, 1)),
            ADMIN, "admin@test.local"
        );
    }

    private Plan planWithAiQuota(String code, int aiQuota) {
        Plan plan = Plan.create(code, code, BigDecimal.TEN);
        plan.updateQuotas(aiQuota, null);
        return plan;
    }

    @Test
    void should_keepTenantsApart_and_hitCacheOnSecondRead() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        when(tenantAdminPort.findPlanCode(a)).thenReturn(Optional.of("PRO"));
        when(tenantAdminPort.findPlanCode(b)).thenReturn(Optional.of("BASIC"));

        assertThat(planLookup.planCodeOf(a)).contains("PRO");
        assertThat(planLookup.planCodeOf(b)).contains("BASIC");
        assertThat(planLookup.planCodeOf(a)).contains("PRO");
        assertThat(planLookup.planCodeOf(b)).contains("BASIC");

        verify(tenantAdminPort, times(1)).findPlanCode(a);
        verify(tenantAdminPort, times(1)).findPlanCode(b);
    }

    @Test
    void should_evictTenantPlanCode_onlyAfterSubscriptionCommit() {
        UUID tenant = UUID.randomUUID();
        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.of("PRO"));
        assertThat(planLookup.planCodeOf(tenant)).contains("PRO");
        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.of("BASIC"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            changeSubscription(tenant, "BASIC");
            // Commit olmadi: temizlik ertelendi, eski deger hala onbellekte.
            assertThat(planLookup.planCodeOf(tenant)).contains("PRO");
        });

        assertThat(planLookup.planCodeOf(tenant)).contains("BASIC");
    }

    @Test
    void should_keepCachedValue_when_subscriptionChangeRollsBack() {
        UUID tenant = UUID.randomUUID();
        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.of("PRO"));
        assertThat(planLookup.planCodeOf(tenant)).contains("PRO");
        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.of("BASIC"));

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            changeSubscription(tenant, "BASIC");
            status.setRollbackOnly();
        });

        assertThat(planLookup.planCodeOf(tenant)).contains("PRO");
    }

    @Test
    void should_notCacheMissingSubscription() {
        UUID tenant = UUID.randomUUID();
        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.empty());
        assertThat(planLookup.planCodeOf(tenant)).isEmpty();

        when(tenantAdminPort.findPlanCode(tenant)).thenReturn(Optional.of("PRO"));
        assertThat(planLookup.planCodeOf(tenant)).contains("PRO");
    }

    @Test
    void should_notCacheMissingPlan() {
        String code = "P-" + UUID.randomUUID();
        when(planRepository.findByCode(code)).thenReturn(Optional.empty());
        assertThat(planLookup.planByCode(code)).isEmpty();

        when(planRepository.findByCode(code)).thenReturn(Optional.of(planWithAiQuota(code, 100)));
        assertThat(planLookup.planByCode(code)).map(PlanSnapshot::monthlyAiQuota).contains(100);
    }

    @Test
    void should_clearAllPlans_afterPlanDeleteCommit() {
        String code = "P-" + UUID.randomUUID();
        when(planRepository.findByCode(code)).thenReturn(Optional.of(planWithAiQuota(code, 100)));
        assertThat(planLookup.planByCode(code)).map(PlanSnapshot::monthlyAiQuota).contains(100);
        when(planRepository.findByCode(code)).thenReturn(Optional.of(planWithAiQuota(code, 200)));

        UUID otherPlanId = UUID.randomUUID();
        when(planRepository.findById(otherPlanId)).thenReturn(Optional.of(planWithAiQuota("BASKA", 1)));
        deletePlanUseCase.execute(otherPlanId, ADMIN, "admin@test.local");

        assertThat(planLookup.planByCode(code)).map(PlanSnapshot::monthlyAiQuota).contains(200);
    }

    @Test
    void should_snapshotBeImmutable_and_keepNullQuota() {
        String code = "P-" + UUID.randomUUID();
        Plan plan = Plan.create(code, code, BigDecimal.TEN);
        plan.updateQuotas(null, 500);
        when(planRepository.findByCode(code)).thenReturn(Optional.of(plan));

        PlanSnapshot snapshot = planLookup.planByCode(code).orElseThrow();

        assertThat(snapshot.monthlyAiQuota()).isNull();
        assertThat(snapshot.monthlyMessageQuota()).isEqualTo(500);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> snapshot.enabledFeatures().clear())
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
```

- [ ] **Step 2: Testin derlenmediğini gör**

Run: `cd backend && mvn -q test -Dtest=PlanLookupCacheTest`
Expected: FAIL. `cannot find symbol ... PlanLookup` / `PlanSnapshot`.

- [ ] **Step 3: `PlanSnapshot`**

```java
package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.platform.feature.Feature;

import java.util.Set;

/**
 * Paketin onbellege konan sade, degistirilemez kopyasi (spec 2026-10-18 §3). Entity onbellege
 * konmaz. Kotada null = sinirsiz (bugunku anlam aynen).
 */
public record PlanSnapshot(String code, Set<Feature> enabledFeatures, Integer monthlyAiQuota, Integer monthlyMessageQuota) {

    public PlanSnapshot {
        enabledFeatures = Set.copyOf(enabledFeatures);
    }

    static PlanSnapshot of(Plan plan) {
        return new PlanSnapshot(plan.getCode(), plan.getEnabledFeatures(), plan.getMonthlyAiQuota(), plan.getMonthlyMessageQuota());
    }
}
```

- [ ] **Step 4: `PlanLookup`**

```java
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
```

- [ ] **Step 5: `PlanFeatureAccessAdapter`'ı `PlanLookup`'a geçir**

Alanlar ve kurucu (`tenantAdminPort` ve `planRepository` alanları kaldırılır):

```java
    private final PlanLookup planLookup;
    private final boolean enforcementEnabled;

    PlanFeatureAccessAdapter(
        PlanLookup planLookup,
        @Value("${features.enforcement.enabled:false}") boolean enforcementEnabled
    ) {
        this.planLookup = planLookup;
        this.enforcementEnabled = enforcementEnabled;
    }
```

`enabledFeatures` gövdesi:

```java
    @Override
    public Set<Feature> enabledFeatures(UUID tenantId) {
        if (!enforcementEnabled) {
            return EnumSet.allOf(Feature.class);
        }
        Optional<String> planCode = planLookup.planCodeOf(tenantId);
        if (planCode.isEmpty()) {
            log.warn("Abonelik bulunamadi, tum ozellikler kapali: tenantId={}", tenantId);
            return EnumSet.noneOf(Feature.class);
        }
        if (TRIAL_PLAN_CODE.equals(planCode.get())) {
            return EnumSet.allOf(Feature.class);
        }
        return planLookup.planByCode(planCode.get())
            .map(PlanSnapshot::enabledFeatures)
            .orElseGet(() -> {
                log.warn("Plan bulunamadi, tum ozellikler kapali: tenantId={}, planCode={}", tenantId, planCode.get());
                return EnumSet.noneOf(Feature.class);
            });
    }
```

Javadoc'un son cümlesi şöyle değişir: `Onbellek: PlanLookup (5 dk; admin degisikligi commit sonrasi temizler).` Kullanılmayan importlar (`PlanRepository`, `TenantAdminPort`) silinir.

- [ ] **Step 6: `PlanUsageQuotaAdapter`'ı `PlanLookup`'a geçir**

İki kurucu:

```java
    private final PlanLookup planLookup;
    private final MonthlyUsageStore store;
    private final QuotaProperties properties;
    private final boolean enforcementEnabled;
    private final Clock clock;

    @Autowired
    PlanUsageQuotaAdapter(
        PlanLookup planLookup,
        MonthlyUsageStore store,
        QuotaProperties properties,
        @Value("${features.enforcement.enabled:false}") boolean enforcementEnabled
    ) {
        this(planLookup, store, properties, enforcementEnabled, Clock.system(ZONE));
    }

    PlanUsageQuotaAdapter(
        PlanLookup planLookup,
        MonthlyUsageStore store,
        QuotaProperties properties,
        boolean enforcementEnabled,
        Clock clock
    ) {
        this.planLookup = planLookup;
        this.store = store;
        this.properties = properties;
        this.enforcementEnabled = enforcementEnabled;
        this.clock = clock;
    }
```

`resolveQuota`:

```java
    /** null = sinirsiz. Optional.map KULLANMA: null kota bos Optional'a donup "plan yok -> 0"a duser. */
    private Integer resolveQuota(UUID tenantId, UsageKind kind) {
        Optional<String> planCode = planLookup.planCodeOf(tenantId);
        if (planCode.isEmpty()) {
            log.warn("Abonelik bulunamadi, kota 0: tenantId={}", tenantId);
            return 0;
        }
        if (PlanFeatureAccessAdapter.TRIAL_PLAN_CODE.equals(planCode.get())) {
            return kind == UsageKind.AI ? properties.trial().ai() : properties.trial().message();
        }
        Optional<PlanSnapshot> plan = planLookup.planByCode(planCode.get());
        if (plan.isEmpty()) {
            log.warn("Plan bulunamadi, kota 0: tenantId={}, planCode={}", tenantId, planCode.get());
            return 0;
        }
        return kind == UsageKind.AI ? plan.get().monthlyAiQuota() : plan.get().monthlyMessageQuota();
    }
```

Javadoc'taki `Onbellek yok: admin degeri degistirince sonraki istek gorur.` cümlesi şöyle olur: `Onbellek: PlanLookup (5 dk; admin degisikligi commit sonrasi temizler).` Kullanılmayan importlar (`Plan`, `PlanRepository`, `TenantAdminPort`) silinir.

- [ ] **Step 7: Mevcut adaptör testlerinde yalnızca kurucu çağrılarını güncelle**

`PlanFeatureAccessAdapterTest`:

```java
    private PlanFeatureAccessAdapter adapter(boolean enforcementEnabled) {
        return new PlanFeatureAccessAdapter(new PlanLookup(tenantAdminPort, planRepository), enforcementEnabled);
    }
```

`PlanUsageQuotaAdapterTest`:

```java
    private PlanUsageQuotaAdapter adapter(boolean enforcement, Clock clock) {
        return new PlanUsageQuotaAdapter(new PlanLookup(tenantAdminPort, planRepository), store, props, enforcement, clock);
    }
```

Bu testlerde `PlanLookup` proxy'siz, yani önbelleksiz. Mock doğrulamaları (`verifyNoInteractions(planRepository)` vb.) aynen geçerli kalır. Başka satıra dokunma. Dosyalarda başka `new PlanFeatureAccessAdapter(` / `new PlanUsageQuotaAdapter(` çağrısı varsa (`grep -rn` ile tüm `src/test` taranır) aynı şekilde güncellenir.

- [ ] **Step 8: Temizlik etiketleri**

`CreatePlanUseCase.execute`, `UpdatePlanUseCase.execute` ve `DeletePlanUseCase.execute` metodlarının her birinde, `@Transactional`'ın hemen üstüne:

```java
    @CacheEvict(cacheNames = CacheNames.PLAN_BY_CODE, allEntries = true)
```

`UpdateTenantSubscriptionUseCase.execute` üstüne:

```java
    @CacheEvict(cacheNames = CacheNames.TENANT_PLAN_CODE, key = "#command.tenantId()")
```

Her dosyaya importlar: `import com.vetos.platform.cache.CacheNames;` ve `import org.springframework.cache.annotation.CacheEvict;`.

`platformadmin/package-info.java`'daki `allowedDependencies` listesinde `"platform::audit"`'in arkasına `, "platform::cache"` eklenir.

- [ ] **Step 9: Testleri çalıştır**

Run: `cd backend && mvn -q test -Dtest='PlanLookupCacheTest,PlanFeatureAccessAdapterTest,PlanUsageQuotaAdapterTest,CreatePlanUseCaseTest,UpdatePlanUseCaseTest,DeletePlanUseCaseTest,UpdateTenantSubscriptionUseCaseTest,ApplicationModulesTest'`
Expected: PASS. PlanLookupCacheTest 7, PlanFeatureAccessAdapterTest 5, PlanUsageQuotaAdapterTest 11, diğerleri mevcut sayılarıyla.

---

### Task 3: TARBİL hastalık listesi önbelleği

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/application/ListTarbilDiseasesUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/package-info.java` (`allowedDependencies`'e `"platform::cache"`)
- Test: `backend/src/test/java/com/vetos/modules/integration/tarbil/application/TarbilDiseaseCacheTest.java`

**Interfaces:**
- Consumes: `CacheNames.TARBIL_DISEASES` (Task 1).
- Produces: davranış değişikliği yok. `execute()` aynı `List<TarbilDiseaseSummary>`'yi döndürür, ikinci çağrıdan itibaren bellekten.

- [ ] **Step 1: Testi yaz**

```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.application.dto.TarbilDiseaseSummary;
import com.vetos.modules.integration.tarbil.domain.TarbilDiseaseRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.verify;

/** Canli docker-compose Postgres gerekir (V72 tohumlu tarbil_disease). */
@SpringBootTest
class TarbilDiseaseCacheTest {

    @MockitoSpyBean private TarbilDiseaseRepository repository;
    @Autowired private ListTarbilDiseasesUseCase useCase;

    @Test
    void should_readTableOnce_and_returnSameImmutableList() {
        List<TarbilDiseaseSummary> first = useCase.execute();
        List<TarbilDiseaseSummary> second = useCase.execute();

        assertThat(first).isNotEmpty();
        assertThat(second).isSameAs(first);
        verify(repository, atMost(1)).findAllOrdered();
        assertThatThrownBy(() -> first.add(first.get(0))).isInstanceOf(UnsupportedOperationException.class);
    }
}
```

Not: `atMost(1)`, çünkü aynı Spring bağlamını paylaşan başka bir test listeyi önceden önbelleğe almış olabilir (spy bean bağlamı bu testte ayrı olsa da güvenli tarafta kalır).

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `cd backend && mvn -q test -Dtest=TarbilDiseaseCacheTest`
Expected: FAIL. `second` aynı nesne değil (`isSameAs`) ve `findAllOrdered` 2 kez çağrıldı.

- [ ] **Step 3: Etiketi ekle**

`ListTarbilDiseasesUseCase.execute()` üstüne, `@Transactional(readOnly = true)`'nun hemen üstüne:

```java
    @Cacheable(CacheNames.TARBIL_DISEASES)
```

Importlar: `com.vetos.platform.cache.CacheNames` ve `org.springframework.cache.annotation.Cacheable`. Sınıf Javadoc'una şu cümle eklenir: `Onbellekli (tum kliniklerde ayni referans tablosu; yalnizca migration degistirir, deploy tazeler).` Liste zaten `Stream.toList()` ile değiştirilemez; dokunma.

`integration/tarbil/package-info.java`'daki `allowedDependencies` listesinde `"platform::audit"`'in arkasına `, "platform::cache"` eklenir.

- [ ] **Step 4: Testleri çalıştır**

Run: `cd backend && mvn -q test -Dtest='TarbilDiseaseCacheTest,ListTarbilDiseasesUseCaseTest,ApplicationModulesTest'`
Expected: PASS.

---

### Task 4: Gelir ve bugünkü satış özetleri (İstanbul saati, DB toplama)

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/billing/domain/BranchRevenueAggregate.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/domain/InvoiceRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/infrastructure/persistence/InvoiceJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/infrastructure/persistence/InvoiceRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/application/GetRevenueSummaryUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/billing/application/GetTodaySalesSummaryUseCase.java`
- Modify: `backend/src/test/java/com/vetos/modules/billing/application/GetTodaySalesSummaryUseCaseTest.java`
- Test: `backend/src/test/java/com/vetos/modules/billing/application/GetRevenueSummaryUseCaseTest.java` (yeni)
- Test: `backend/src/test/java/com/vetos/DashboardSummaryQueriesTest.java` (yeni; Task 5 genişletir)

**Interfaces:**
- Produces:
  - `public record BranchRevenueAggregate(UUID branchId, BigDecimal totalAmount)`: `null` toplam `ZERO`'ya normalize edilir.
  - `InvoiceRepository.sumIssuedByBranchBetween(UUID tenantId, Collection<InvoiceStatus> statuses, Instant start, Instant end) : List<BranchRevenueAggregate>`
  - Use case kurucuları:
    - `GetRevenueSummaryUseCase(InvoiceRepository, BranchLookupPort)` (public, `@Autowired`) ve `(…, Clock)` (paket görünürlüğünde)
    - `GetTodaySalesSummaryUseCase(InvoiceRepository)` (public, `@Autowired`) ve `(InvoiceRepository, Clock)`
  - `DashboardSummaryQueriesTest` (Task 5 bu dosyaya test ekler): fixture yardımcıları `createTenantFixture(String)`, `createInvoice(Fixture, InvoiceStatus, Instant, BigDecimal)`, `purge` (`@AfterEach`).

- [ ] **Step 1: Gelir özeti birim testini yaz (yeni dosya)**

```java
package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.RevenueSummary;
import com.vetos.modules.billing.domain.BranchRevenueAggregate;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.SalesAggregate;
import com.vetos.modules.tenant.domain.BranchLookupPort;
import com.vetos.modules.tenant.domain.BranchSummary;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetRevenueSummaryUseCaseTest {

    private static final UUID TENANT = UUID.randomUUID();
    // 2026-10-31T22:30Z = Istanbul 2026-11-01 01:30 -> "bu ay" KASIM (UTC'ye gore Ekim olurdu).
    private static final Clock NOV_1_NIGHT = Clock.fixed(Instant.parse("2026-10-31T22:30:00Z"), ZoneOffset.UTC);
    private static final Instant NOV_START = Instant.parse("2026-10-31T21:00:00Z");
    private static final Instant DEC_START = Instant.parse("2026-11-30T21:00:00Z");
    private static final Instant OCT_START = Instant.parse("2026-09-30T21:00:00Z");

    private final InvoiceRepository invoiceRepository = mock(InvoiceRepository.class);
    private final BranchLookupPort branchLookupPort = mock(BranchLookupPort.class);

    @Test
    void should_useIstanbulMonthBoundaries_and_dbAggregates() {
        UUID merkez = UUID.randomUUID();
        UUID sube = UUID.randomUUID();
        when(invoiceRepository.sumIssuedBetween(eq(TENANT), any(), any(), any())).thenReturn(new SalesAggregate(null, 0));
        when(invoiceRepository.sumIssuedBetween(eq(TENANT), any(), eq(NOV_START), eq(DEC_START)))
            .thenReturn(new SalesAggregate(new BigDecimal("700"), 3));
        when(invoiceRepository.sumIssuedBetween(eq(TENANT), any(), eq(OCT_START), eq(NOV_START)))
            .thenReturn(new SalesAggregate(new BigDecimal("400"), 2));
        when(invoiceRepository.sumIssuedByBranchBetween(eq(TENANT), any(), eq(NOV_START), eq(DEC_START)))
            .thenReturn(List.of(new BranchRevenueAggregate(sube, new BigDecimal("200")), new BranchRevenueAggregate(merkez, new BigDecimal("500"))));
        when(branchLookupPort.findSummaryById(merkez)).thenReturn(new BranchSummary(merkez, "Merkez"));
        when(branchLookupPort.findSummaryById(sube)).thenReturn(new BranchSummary(sube, "Sube"));

        RevenueSummary result = new GetRevenueSummaryUseCase(invoiceRepository, branchLookupPort, NOV_1_NIGHT).execute(TENANT);

        assertThat(result.monthlyTrend()).extracting(m -> m.month())
            .containsExactly("2026-06", "2026-07", "2026-08", "2026-09", "2026-10", "2026-11");
        assertThat(result.monthlyTrend().get(0).revenue()).isEqualByComparingTo("0");
        assertThat(result.currentMonthRevenue()).isEqualByComparingTo("700");
        assertThat(result.previousMonthRevenue()).isEqualByComparingTo("400");
        assertThat(result.branchBreakdown()).extracting(b -> b.branchName()).containsExactly("Merkez", "Sube");
    }
}
```

Not: `BranchSummary(UUID id, String name)` — `com.vetos.modules.tenant.domain` (doğrulandı).

- [ ] **Step 2: Bugünkü satış testini İstanbul gününe güncelle**

`GetTodaySalesSummaryUseCaseTest`'te iki test de `new GetTodaySalesSummaryUseCase(invoiceRepository, CLOCK)` ile kurulur. Sınıfa şu sabitler eklenir:

```java
    // 2026-10-17T21:30Z = Istanbul 2026-10-18 00:30 -> "bugun" 18 Ekim (UTC'ye gore 17 olurdu).
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T21:30:00Z"), ZoneOffset.UTC);
    private static final Instant ISTANBUL_TODAY_START = Instant.parse("2026-10-17T21:00:00Z");
```

`should_queryTodayRangeWithRevenueStatuses` içindeki `startOfToday` hesabı silinir. Doğrulama şu olur:

```java
        verify(invoiceRepository).sumIssuedBetween(
            eq(tenantId), statuses.capture(), eq(ISTANBUL_TODAY_START), eq(ISTANBUL_TODAY_START.plusSeconds(86400))
        );
```

Sınıf Javadoc'undaki `[bugun 00:00, yarin 00:00)` ifadesi `[bugun 00:00, yarin 00:00) Istanbul saatiyle` olur. Kullanılmayan `LocalDate` importu silinir, `Clock` importu eklenir.

- [ ] **Step 3: Birim testlerin başarısız olduğunu gör**

Run: `cd backend && mvn -q test -Dtest='GetRevenueSummaryUseCaseTest,GetTodaySalesSummaryUseCaseTest'`
Expected: FAIL. Derleme hatası: `BranchRevenueAggregate`, `sumIssuedByBranchBetween` ve Clock'lu kurucular yok.

- [ ] **Step 4: Domain kaydı ve repository**

```java
package com.vetos.modules.billing.domain;

import java.math.BigDecimal;
import java.util.UUID;

/** Sube bazli ciro toplami; toplama veritabaninda (bkz. InvoiceRepository#sumIssuedByBranchBetween). */
public record BranchRevenueAggregate(UUID branchId, BigDecimal totalAmount) {

    public BranchRevenueAggregate {
        totalAmount = totalAmount == null ? BigDecimal.ZERO : totalAmount;
    }
}
```

`InvoiceRepository`'ye, `sumIssuedBetween`'ün altına:

```java
    /**
     * [start, end) araliginda kesilmis, durumu {@code statuses} icindeki faturalarin sube bazli
     * toplami. Yalnizca faturasi olan subeler doner. Toplama veritabaninda yapilir.
     */
    List<BranchRevenueAggregate> sumIssuedByBranchBetween(UUID tenantId, Collection<InvoiceStatus> statuses, Instant start, Instant end);
```

`InvoiceJpaRepository`'ye:

```java
    @Query(
        "SELECT new com.vetos.modules.billing.domain.BranchRevenueAggregate(i.branchId, SUM(i.totalAmount)) " +
            "FROM Invoice i WHERE i.tenantId = :tenantId AND i.status IN :statuses " +
            "AND i.issuedAt >= :start AND i.issuedAt < :end GROUP BY i.branchId"
    )
    List<BranchRevenueAggregate> sumIssuedByBranchBetween(
        @Param("tenantId") UUID tenantId, @Param("statuses") Collection<InvoiceStatus> statuses,
        @Param("start") Instant start, @Param("end") Instant end
    );
```

`InvoiceRepositoryAdapter`'a:

```java
    @Override
    public List<BranchRevenueAggregate> sumIssuedByBranchBetween(
        UUID tenantId, Collection<InvoiceStatus> statuses, Instant start, Instant end
    ) {
        return jpaRepository.sumIssuedByBranchBetween(tenantId, statuses, start, end);
    }
```

(Gerekli importlar: `BranchRevenueAggregate`.)

- [ ] **Step 5: `GetRevenueSummaryUseCase`'i yeniden yaz**

Sınıfın tamamı (paket ve Javadoc ilk paragrafı korunur):

```java
package com.vetos.modules.billing.application;

import com.vetos.modules.billing.application.dto.BranchRevenue;
import com.vetos.modules.billing.application.dto.MonthlyRevenue;
import com.vetos.modules.billing.application.dto.RevenueSummary;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.tenant.domain.BranchLookupPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * @docs/requirements.md 4.12 "Raporlama & Analitik" -- dashboard'daki
 * "Isletme ozeti" sekmesi eskiden sabit/uydurma verilerle doluydu, bu use-case
 * gercek Invoice kayitlarindan hesaplanan aylik ciro trendi ve sube bazli
 * ciro kirilimi uretir.
 * Faz 6 (spec 2026-10-18 §5): toplama veritabaninda (ay basina SUM + sube bazli SUM);
 * ay sinirlari Istanbul saatiyle.
 */
@Service
public class GetRevenueSummaryUseCase {

    static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final Set<InvoiceStatus> REVENUE_STATUSES = EnumSet.of(
        InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID
    );
    private static final int TREND_MONTHS = 6;

    private final InvoiceRepository invoiceRepository;
    private final BranchLookupPort branchLookupPort;
    private final Clock clock;

    @Autowired
    public GetRevenueSummaryUseCase(InvoiceRepository invoiceRepository, BranchLookupPort branchLookupPort) {
        this(invoiceRepository, branchLookupPort, Clock.system(ISTANBUL));
    }

    GetRevenueSummaryUseCase(InvoiceRepository invoiceRepository, BranchLookupPort branchLookupPort, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.branchLookupPort = branchLookupPort;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public RevenueSummary execute(UUID tenantId) {
        YearMonth currentMonth = YearMonth.now(clock.withZone(ISTANBUL));

        List<MonthlyRevenue> trend = IntStream.rangeClosed(0, TREND_MONTHS - 1)
            .mapToObj(i -> currentMonth.minusMonths(TREND_MONTHS - 1L - i))
            .map(month -> new MonthlyRevenue(month.toString(), invoiceRepository.sumIssuedBetween(
                tenantId, REVENUE_STATUSES, startOf(month), startOf(month.plusMonths(1))
            ).totalAmount()))
            .toList();

        List<BranchRevenue> branchBreakdown = invoiceRepository.sumIssuedByBranchBetween(
                tenantId, REVENUE_STATUSES, startOf(currentMonth), startOf(currentMonth.plusMonths(1))
            ).stream()
            .map(a -> new BranchRevenue(a.branchId(), branchLookupPort.findSummaryById(a.branchId()).name(), a.totalAmount()))
            .sorted(Comparator.comparing(BranchRevenue::revenue).reversed())
            .toList();

        return new RevenueSummary(
            trend, branchBreakdown, trend.get(TREND_MONTHS - 1).revenue(), trend.get(TREND_MONTHS - 2).revenue()
        );
    }

    private static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ISTANBUL).toInstant();
    }
}
```

- [ ] **Step 6: `GetTodaySalesSummaryUseCase`'i İstanbul gününe al**

`@RequiredArgsConstructor` kaldırılır. Alanlar, kurucular ve `execute`:

```java
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final InvoiceRepository invoiceRepository;
    private final Clock clock;

    @Autowired
    public GetTodaySalesSummaryUseCase(InvoiceRepository invoiceRepository) {
        this(invoiceRepository, Clock.system(ISTANBUL));
    }

    GetTodaySalesSummaryUseCase(InvoiceRepository invoiceRepository, Clock clock) {
        this.invoiceRepository = invoiceRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public TodaySalesSummary execute(UUID tenantId) {
        LocalDate today = LocalDate.now(clock.withZone(ISTANBUL));
        Instant startOfToday = today.atStartOfDay(ISTANBUL).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(ISTANBUL).toInstant();
        SalesAggregate aggregate = invoiceRepository.sumIssuedBetween(
            tenantId, REVENUE_STATUSES, startOfToday, startOfTomorrow
        );
        return new TodaySalesSummary(aggregate.totalAmount(), Math.toIntExact(aggregate.count()));
    }
```

Importlar: `Clock`, `ZoneId`, `Autowired` eklenir; `ZoneOffset` ve `RequiredArgsConstructor` silinir. Javadoc'a şu cümle eklenir: `Gun sinirlari Istanbul saatiyle (Faz 6).`

- [ ] **Step 7: Birim testleri çalıştır**

Run: `cd backend && mvn -q test -Dtest='GetRevenueSummaryUseCaseTest,GetTodaySalesSummaryUseCaseTest'`
Expected: PASS (1 + 2).

- [ ] **Step 8: DB sorgu testini yaz (gerçek Postgres)**

```java
package com.vetos;

import com.vetos.modules.billing.domain.BranchRevenueAggregate;
import com.vetos.modules.billing.domain.Invoice;
import com.vetos.modules.billing.domain.InvoiceRepository;
import com.vetos.modules.billing.domain.InvoiceStatus;
import com.vetos.modules.billing.domain.SalesAggregate;
import com.vetos.modules.patient.domain.Owner;
import com.vetos.modules.patient.domain.OwnerRepository;
import com.vetos.modules.tenant.domain.Branch;
import com.vetos.modules.tenant.domain.BranchRepository;
import com.vetos.modules.tenant.domain.StaffRole;
import com.vetos.modules.tenant.domain.StaffUser;
import com.vetos.modules.tenant.domain.StaffUserRepository;
import com.vetos.modules.tenant.domain.Tenant;
import com.vetos.modules.tenant.domain.TenantRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Faz 6 ozet sorgularinin gercek Postgres'te dogrulugu: Istanbul ay siniri, durum filtresi,
 * sube gruplama, kiracilar arasi sizinti yok. Kayit zamanlari native UPDATE ile ayarlanir.
 * Canli docker-compose Postgres gerekir.
 */
@SpringBootTest
class DashboardSummaryQueriesTest extends TenantScopedTestSupport {

    // Istanbul Kasim 2026 = [2026-10-31T21:00Z, 2026-11-30T21:00Z)
    static final Instant NOV_START = Instant.parse("2026-10-31T21:00:00Z");
    static final Instant DEC_START = Instant.parse("2026-11-30T21:00:00Z");
    static final Set<InvoiceStatus> REVENUE = EnumSet.of(InvoiceStatus.ISSUED, InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.PAID);

    @Autowired TenantRepository tenantRepository;
    @Autowired BranchRepository branchRepository;
    @Autowired OwnerRepository ownerRepository;
    @Autowired StaffUserRepository staffUserRepository;
    @Autowired InvoiceRepository invoiceRepository;
    @PersistenceContext EntityManager entityManager;

    record Fixture(UUID tenantId, UUID branchId, UUID ownerId, UUID staffUserId) {}

    private final List<UUID> createdTenantIds = new ArrayList<>();

    Fixture createTenantFixture(String label) {
        UUID tenantId = inRootSession(() -> tenantRepository.save(Tenant.register(label, null)).getId());
        createdTenantIds.add(tenantId);
        UUID branchId = inRootSession(() -> branchRepository.save(Branch.create(tenantId, label + " Merkez")).getId());
        UUID ownerId = asTenant(tenantId, () -> ownerRepository.save(
            Owner.register(tenantId, label + " Sahip", "05551234567", null, null)).getId());
        UUID staffUserId = asTenant(tenantId, () -> staffUserRepository.save(StaffUser.register(
            tenantId, branchId, "Dr. " + label, "ozet-" + UUID.randomUUID() + "@test.local", "hash", StaffRole.VET
        )).getId());
        return new Fixture(tenantId, branchId, ownerId, staffUserId);
    }

    UUID createBranch(Fixture f, String name) {
        return inRootSession(() -> branchRepository.save(Branch.create(f.tenantId(), name)).getId());
    }

    UUID createInvoice(Fixture f, UUID branchId, InvoiceStatus status, Instant issuedAt, String amount) {
        UUID id = asTenant(f.tenantId(), () -> invoiceRepository.save(
            Invoice.createDraft(f.tenantId(), branchId, f.ownerId(), null, f.staffUserId())).getId());
        inRootSessionVoid(() -> entityManager.createNativeQuery(
                "UPDATE invoices SET status = :s, issued_at = :at, total_amount = :amt WHERE id = :id")
            .setParameter("s", status.name()).setParameter("at", issuedAt)
            .setParameter("amt", new BigDecimal(amount)).setParameter("id", id).executeUpdate());
        return id;
    }

    @AfterEach
    void purge() {
        for (UUID t : createdTenantIds) {
            inRootSessionVoid(() -> List.of(
                "DELETE FROM appointments WHERE tenant_id = :t",
                "DELETE FROM service_types WHERE tenant_id = :t",
                "DELETE FROM invoices WHERE tenant_id = :t",
                "DELETE FROM patients WHERE owner_id IN (SELECT id FROM owners WHERE tenant_id = :t)",
                "DELETE FROM owners WHERE tenant_id = :t",
                "DELETE FROM subscriptions WHERE tenant_id = :t",
                "DELETE FROM staff_users WHERE tenant_id = :t",
                "DELETE FROM branches WHERE tenant_id = :t",
                "DELETE FROM tenants WHERE id = :t"
            ).forEach(sql -> entityManager.createNativeQuery(sql).setParameter("t", t).executeUpdate()));
        }
        createdTenantIds.clear();
    }

    @Test
    void revenue_respectsIstanbulMonth_status_branch_and_tenant() {
        Fixture a = createTenantFixture("Ozet A");
        Fixture b = createTenantFixture("Ozet B");
        UUID subeA = createBranch(a, "Ozet A Sube");

        createInvoice(a, a.branchId(), InvoiceStatus.PAID, Instant.parse("2026-10-31T21:30:00Z"), "100");      // 1 Kasim 00:30 -> Kasim
        createInvoice(a, a.branchId(), InvoiceStatus.ISSUED, Instant.parse("2026-11-30T20:30:00Z"), "50");     // 30 Kasim 23:30 -> Kasim
        createInvoice(a, subeA, InvoiceStatus.PARTIALLY_PAID, Instant.parse("2026-11-10T09:00:00Z"), "30");
        createInvoice(a, a.branchId(), InvoiceStatus.PAID, Instant.parse("2026-10-31T20:30:00Z"), "999");      // 31 Ekim 23:30 -> Ekim
        createInvoice(a, a.branchId(), InvoiceStatus.VOID, Instant.parse("2026-11-10T09:00:00Z"), "777"); // gelir sayilmaz
        createInvoice(b, b.branchId(), InvoiceStatus.PAID, Instant.parse("2026-11-10T09:00:00Z"), "555");      // baska kiraci

        SalesAggregate nov = asTenant(a.tenantId(), () -> invoiceRepository.sumIssuedBetween(a.tenantId(), REVENUE, NOV_START, DEC_START));
        assertThat(nov.totalAmount()).isEqualByComparingTo("180");
        assertThat(nov.count()).isEqualTo(3);

        List<BranchRevenueAggregate> byBranch = asTenant(a.tenantId(),
            () -> invoiceRepository.sumIssuedByBranchBetween(a.tenantId(), REVENUE, NOV_START, DEC_START));
        assertThat(byBranch).hasSize(2);
        assertThat(byBranch).filteredOn(x -> x.branchId().equals(a.branchId())).singleElement()
            .satisfies(x -> assertThat(x.totalAmount()).isEqualByComparingTo("150"));
        assertThat(byBranch).filteredOn(x -> x.branchId().equals(subeA)).singleElement()
            .satisfies(x -> assertThat(x.totalAmount()).isEqualByComparingTo("30"));

        assertThat(asTenant(a.tenantId(), () -> invoiceRepository.sumIssuedByBranchBetween(
            a.tenantId(), REVENUE, Instant.parse("2026-12-31T21:00:00Z"), Instant.parse("2027-01-31T21:00:00Z")))).isEmpty();
    }
}
```

Uyum notları (testin mantığını değiştirmeden düzeltilir):
- İptal durumu `InvoiceStatus.VOID` (doğrulandı).
- `invoices` tablosunda `total_amount`'a bağlı bir CHECK ya da başka zorunlu kolon (ör. `subtotal`) varsa UPDATE'e eşit değeri ekle.
- `Tenant.register`, `Branch.create`, `Owner.register`, `StaffUser.register` ve `Invoice.createDraft` imzaları `TenantIsolationTest`'tekilerle aynıdır; oradan doğrula.

- [ ] **Step 9: DB testini çalıştır**

Run: `cd backend && mvn -q test -Dtest=DashboardSummaryQueriesTest`
Expected: PASS (1 test).

---

### Task 5: Hasta büyümesi ve randevu özetleri (COUNT, İstanbul saati)

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/patient/domain/PatientRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/infrastructure/persistence/PatientJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/infrastructure/persistence/PatientRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/patient/application/GetPatientGrowthSummaryUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/appointment/domain/AppointmentRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/appointment/infrastructure/persistence/AppointmentJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/appointment/infrastructure/persistence/AppointmentRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/appointment/application/GetAppointmentActivitySummaryUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/patient/application/GetPatientGrowthSummaryUseCaseTest.java` (yeni)
- Test: `backend/src/test/java/com/vetos/modules/appointment/application/GetAppointmentActivitySummaryUseCaseTest.java` (yeni)
- Modify (test ekle): `backend/src/test/java/com/vetos/DashboardSummaryQueriesTest.java`

**Interfaces:**
- Consumes: `DashboardSummaryQueriesTest`'in `Fixture`, `createTenantFixture`, `purge` yardımcıları (Task 4).
- Produces:
  - `PatientRepository.countByTenantIdAndCreatedAtRange(UUID tenantId, Instant rangeStart, Instant rangeEnd) : long`
  - `AppointmentRepository.countByTenantIdAndDateRange(UUID tenantId, Instant rangeStart, Instant rangeEnd) : long`
  - `AppointmentRepository.countByTenantIdAndDateRangeAndStatus(UUID tenantId, Instant rangeStart, Instant rangeEnd, AppointmentStatus status) : long`
  - Use case kurucuları: public `@Autowired` (repository) ve paket görünürlüğünde `(repository, Clock)`.
  - `PatientRepository.findByTenantIdAndCreatedAtRange` KALDIRILIR (tek kullanıcısı büyüme özetiydi). Önce `grep -rn "findByTenantIdAndCreatedAtRange" backend/src` ile başka kullanıcı olmadığını doğrula; varsa kaldırma ve ledger'a not düş. `AppointmentRepository.findByTenantIdAndDateRange` KALIR (kampanya adayları kullanıyor).

- [ ] **Step 1: Birim testleri yaz**

```java
package com.vetos.modules.patient.application;

import com.vetos.modules.patient.application.dto.PatientGrowthSummary;
import com.vetos.modules.patient.domain.PatientRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetPatientGrowthSummaryUseCaseTest {

    private static final UUID TENANT = UUID.randomUUID();
    // Istanbul 2026-11-01 01:30 -> bu ay Kasim
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T22:30:00Z"), ZoneOffset.UTC);

    @Test
    void should_countWithIstanbulMonthBoundaries() {
        PatientRepository repository = mock(PatientRepository.class);
        when(repository.countByTenantIdAndCreatedAtRange(TENANT,
            Instant.parse("2026-10-31T21:00:00Z"), Instant.parse("2026-11-30T21:00:00Z"))).thenReturn(4L);
        when(repository.countByTenantIdAndCreatedAtRange(TENANT,
            Instant.parse("2026-09-30T21:00:00Z"), Instant.parse("2026-10-31T21:00:00Z"))).thenReturn(9L);

        PatientGrowthSummary result = new GetPatientGrowthSummaryUseCase(repository, CLOCK).execute(TENANT);

        assertThat(result.newPatientsThisMonth()).isEqualTo(4);
        assertThat(result.newPatientsLastMonth()).isEqualTo(9);
    }
}
```

```java
package com.vetos.modules.appointment.application;

import com.vetos.modules.appointment.application.dto.AppointmentActivitySummary;
import com.vetos.modules.appointment.domain.AppointmentRepository;
import com.vetos.modules.appointment.domain.AppointmentStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetAppointmentActivitySummaryUseCaseTest {

    private static final UUID TENANT = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-31T22:30:00Z"), ZoneOffset.UTC);
    private static final Instant OCT = Instant.parse("2026-09-30T21:00:00Z");
    private static final Instant NOV = Instant.parse("2026-10-31T21:00:00Z");
    private static final Instant DEC = Instant.parse("2026-11-30T21:00:00Z");

    private final AppointmentRepository repository = mock(AppointmentRepository.class);

    @Test
    void should_countTotalsAndNoShows_perIstanbulMonth() {
        when(repository.countByTenantIdAndDateRange(TENANT, NOV, DEC)).thenReturn(8L);
        when(repository.countByTenantIdAndDateRangeAndStatus(TENANT, NOV, DEC, AppointmentStatus.NO_SHOW)).thenReturn(2L);
        when(repository.countByTenantIdAndDateRange(TENANT, OCT, NOV)).thenReturn(10L);
        when(repository.countByTenantIdAndDateRangeAndStatus(TENANT, OCT, NOV, AppointmentStatus.NO_SHOW)).thenReturn(1L);

        AppointmentActivitySummary result = new GetAppointmentActivitySummaryUseCase(repository, CLOCK).execute(TENANT);

        assertThat(result.currentMonthCount()).isEqualTo(8);
        assertThat(result.previousMonthCount()).isEqualTo(10);
        assertThat(result.currentMonthNoShowRate()).isEqualTo(0.25);
        assertThat(result.previousMonthNoShowRate()).isEqualTo(0.1);
    }

    @Test
    void should_returnZeroRate_when_noAppointments() {
        AppointmentActivitySummary result = new GetAppointmentActivitySummaryUseCase(repository, CLOCK).execute(TENANT);

        assertThat(result.currentMonthCount()).isZero();
        assertThat(result.currentMonthNoShowRate()).isEqualTo(0.0);
        assertThat(result.previousMonthNoShowRate()).isEqualTo(0.0);
    }
}
```

(Mockito'nun stub'lanmamış `long` dönüşü `0`'dır; ikinci test buna dayanır.)

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `cd backend && mvn -q test -Dtest='GetPatientGrowthSummaryUseCaseTest,GetAppointmentActivitySummaryUseCaseTest'`
Expected: FAIL. Derleme hatası: count metodları ve Clock'lu kurucular yok.

- [ ] **Step 3: Repository sayma metodları**

`PatientRepository`: `findByTenantIdAndCreatedAtRange` satırı şununla değiştirilir:

```java
    /** [rangeStart, rangeEnd) araliginda eklenen hasta sayisi; sayma veritabaninda. */
    long countByTenantIdAndCreatedAtRange(UUID tenantId, Instant rangeStart, Instant rangeEnd);
```

`PatientJpaRepository`: native `findByTenantIdAndCreatedAtRange` sorgusu şununla değiştirilir:

```java
    @Query(
        "SELECT COUNT(p) FROM Patient p WHERE p.tenantId = :tenantId " +
            "AND p.createdAt >= :rangeStart AND p.createdAt < :rangeEnd"
    )
    long countByTenantIdAndCreatedAtRange(
        @Param("tenantId") UUID tenantId, @Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd
    );
```

`PatientRepositoryAdapter`: eski metodun yerine:

```java
    @Override
    public long countByTenantIdAndCreatedAtRange(UUID tenantId, Instant rangeStart, Instant rangeEnd) {
        return jpaRepository.countByTenantIdAndCreatedAtRange(tenantId, rangeStart, rangeEnd);
    }
```

`AppointmentRepository`'ye:

```java
    long countByTenantIdAndDateRange(UUID tenantId, Instant rangeStart, Instant rangeEnd);
    long countByTenantIdAndDateRangeAndStatus(UUID tenantId, Instant rangeStart, Instant rangeEnd, AppointmentStatus status);
```

`AppointmentJpaRepository`'ye:

```java
    @Query(
        "SELECT COUNT(a) FROM Appointment a WHERE a.tenantId = :tenantId " +
            "AND a.scheduledStart >= :rangeStart AND a.scheduledStart < :rangeEnd"
    )
    long countByTenantIdAndDateRange(
        @Param("tenantId") UUID tenantId, @Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd
    );

    @Query(
        "SELECT COUNT(a) FROM Appointment a WHERE a.tenantId = :tenantId " +
            "AND a.scheduledStart >= :rangeStart AND a.scheduledStart < :rangeEnd AND a.status = :status"
    )
    long countByTenantIdAndDateRangeAndStatus(
        @Param("tenantId") UUID tenantId, @Param("rangeStart") Instant rangeStart, @Param("rangeEnd") Instant rangeEnd,
        @Param("status") AppointmentStatus status
    );
```

`AppointmentRepositoryAdapter`'a iki `@Override` metodu `jpaRepository`'ye aynen yönlendirir.

- [ ] **Step 4: Use case'leri yeniden yaz**

`GetPatientGrowthSummaryUseCase`: `@RequiredArgsConstructor` kaldırılır. Gövde:

```java
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final PatientRepository patientRepository;
    private final Clock clock;

    @Autowired
    public GetPatientGrowthSummaryUseCase(PatientRepository patientRepository) {
        this(patientRepository, Clock.system(ISTANBUL));
    }

    GetPatientGrowthSummaryUseCase(PatientRepository patientRepository, Clock clock) {
        this.patientRepository = patientRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PatientGrowthSummary execute(UUID tenantId) {
        YearMonth currentMonth = YearMonth.now(clock.withZone(ISTANBUL));
        YearMonth previousMonth = currentMonth.minusMonths(1);
        long thisMonth = patientRepository.countByTenantIdAndCreatedAtRange(
            tenantId, startOf(currentMonth), startOf(currentMonth.plusMonths(1))
        );
        long lastMonth = patientRepository.countByTenantIdAndCreatedAtRange(
            tenantId, startOf(previousMonth), startOf(currentMonth)
        );
        return new PatientGrowthSummary(Math.toIntExact(thisMonth), Math.toIntExact(lastMonth));
    }

    private static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ISTANBUL).toInstant();
    }
```

Importlar: `Clock`, `Instant`, `ZoneId`, `Autowired` eklenir; `ZoneOffset` ve `RequiredArgsConstructor` silinir. Javadoc'a şu cümle eklenir: `Faz 6: sayma veritabaninda, ay sinirlari Istanbul saatiyle.`

`GetAppointmentActivitySummaryUseCase`: `@RequiredArgsConstructor` kaldırılır. Gövde:

```java
    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;

    @Autowired
    public GetAppointmentActivitySummaryUseCase(AppointmentRepository appointmentRepository) {
        this(appointmentRepository, Clock.system(ISTANBUL));
    }

    GetAppointmentActivitySummaryUseCase(AppointmentRepository appointmentRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AppointmentActivitySummary execute(UUID tenantId) {
        YearMonth currentMonth = YearMonth.now(clock.withZone(ISTANBUL));
        YearMonth previousMonth = currentMonth.minusMonths(1);
        MonthCounts current = countsFor(tenantId, currentMonth);
        MonthCounts previous = countsFor(tenantId, previousMonth);
        return new AppointmentActivitySummary(
            Math.toIntExact(current.total()), Math.toIntExact(previous.total()), current.noShowRate(), previous.noShowRate()
        );
    }

    private MonthCounts countsFor(UUID tenantId, YearMonth month) {
        Instant start = startOf(month);
        Instant end = startOf(month.plusMonths(1));
        return new MonthCounts(
            appointmentRepository.countByTenantIdAndDateRange(tenantId, start, end),
            appointmentRepository.countByTenantIdAndDateRangeAndStatus(tenantId, start, end, AppointmentStatus.NO_SHOW)
        );
    }

    private record MonthCounts(long total, long noShow) {
        double noShowRate() {
            return total == 0 ? 0.0 : noShow / (double) total;
        }
    }

    private static Instant startOf(YearMonth month) {
        return month.atDay(1).atStartOfDay(ISTANBUL).toInstant();
    }
```

Importlar: `Clock`, `Instant`, `Autowired` eklenir; `Appointment`, `List` ve `RequiredArgsConstructor` silinir. Javadoc'a şu cümle eklenir: `Faz 6: sayma veritabaninda.`

- [ ] **Step 5: Birim testleri çalıştır**

Run: `cd backend && mvn -q test -Dtest='GetPatientGrowthSummaryUseCaseTest,GetAppointmentActivitySummaryUseCaseTest'`
Expected: PASS (1 + 2).

- [ ] **Step 6: DB sorgu testlerini `DashboardSummaryQueriesTest`'e ekle**

Ek alanlar (`@Autowired`): `SpeciesRepository speciesRepository`, `PatientRepository patientRepository`, `ServiceTypeRepository serviceTypeRepository`, `AppointmentRepository appointmentRepository`. Ek importlar: `Patient`, `PatientRepository`, `Sex`, `SpeciesRepository` (`com.vetos.modules.patient.domain`); `Appointment`, `AppointmentRepository`, `AppointmentSource`, `AppointmentStatus`, `ServiceType`, `ServiceTypeRepository` (`com.vetos.modules.appointment.domain`).

```java
    UUID createPatient(Fixture f, Instant createdAt) {
        UUID speciesId = inRootSession(() -> speciesRepository.findAll().get(0).getId());
        UUID id = asTenant(f.tenantId(), () -> patientRepository.save(
            Patient.register(f.tenantId(), f.ownerId(), speciesId, null, "Tekir", Sex.FEMALE, null)).getId());
        inRootSessionVoid(() -> entityManager.createNativeQuery("UPDATE patients SET created_at = :at WHERE id = :id")
            .setParameter("at", createdAt).setParameter("id", id).executeUpdate());
        return id;
    }

    UUID createAppointment(Fixture f, Instant start, AppointmentStatus status) {
        UUID patientId = createPatient(f, Instant.parse("2026-01-01T09:00:00Z"));
        UUID serviceTypeId = asTenant(f.tenantId(), () -> serviceTypeRepository.save(
            ServiceType.create(f.tenantId(), "Muayene", 30, BigDecimal.TEN)).getId());
        UUID id = asTenant(f.tenantId(), () -> appointmentRepository.save(Appointment.schedule(
            f.tenantId(), f.branchId(), patientId, f.ownerId(), f.staffUserId(), serviceTypeId,
            start, start.plusSeconds(1800), AppointmentSource.PHONE, null)).getId());
        inRootSessionVoid(() -> entityManager.createNativeQuery("UPDATE appointments SET status = :s WHERE id = :id")
            .setParameter("s", status.name()).setParameter("id", id).executeUpdate());
        return id;
    }

    @Test
    void patientCount_respectsIstanbulMonth_and_tenant() {
        Fixture a = createTenantFixture("Ozet A");
        Fixture b = createTenantFixture("Ozet B");
        createPatient(a, Instant.parse("2026-10-31T21:30:00Z")); // 1 Kasim 00:30 -> Kasim
        createPatient(a, Instant.parse("2026-11-15T09:00:00Z"));
        createPatient(a, Instant.parse("2026-10-31T20:30:00Z")); // 31 Ekim 23:30 -> Ekim
        createPatient(b, Instant.parse("2026-11-15T09:00:00Z")); // baska kiraci

        assertThat(asTenant(a.tenantId(), () -> patientRepository.countByTenantIdAndCreatedAtRange(a.tenantId(), NOV_START, DEC_START)))
            .isEqualTo(2);
    }

    @Test
    void appointmentCounts_totalAndNoShow_perTenant() {
        Fixture a = createTenantFixture("Ozet A");
        Fixture b = createTenantFixture("Ozet B");
        createAppointment(a, Instant.parse("2026-11-03T07:00:00Z"), AppointmentStatus.NO_SHOW);
        createAppointment(a, Instant.parse("2026-11-04T07:00:00Z"), AppointmentStatus.COMPLETED);
        createAppointment(a, Instant.parse("2026-10-31T20:00:00Z"), AppointmentStatus.NO_SHOW); // Ekim
        createAppointment(b, Instant.parse("2026-11-05T07:00:00Z"), AppointmentStatus.NO_SHOW); // baska kiraci

        assertThat(asTenant(a.tenantId(), () -> appointmentRepository.countByTenantIdAndDateRange(a.tenantId(), NOV_START, DEC_START)))
            .isEqualTo(2);
        assertThat(asTenant(a.tenantId(), () -> appointmentRepository.countByTenantIdAndDateRangeAndStatus(
            a.tenantId(), NOV_START, DEC_START, AppointmentStatus.NO_SHOW))).isEqualTo(1);
    }
```

Uyum notları (test mantığı değişmeden):
- `AppointmentStatus.COMPLETED` ve `NO_SHOW` mevcut (doğrulandı).
- `Appointment.schedule` ve `ServiceType.create` imzaları `TenantIsolationTest`'tekilerle aynıdır.
- Purge zaten `appointments`, `service_types` ve `patients` tablolarını siliyor (Task 4).

- [ ] **Step 7: DB testini çalıştır**

Run: `cd backend && mvn -q test -Dtest=DashboardSummaryQueriesTest`
Expected: PASS (3 test).

---

### Task 6: Dokümantasyon ve tam doğrulama

**Files:**
- Modify: `docs/deployment.md` ("Dış servis koruması (Faz 5)" bölümünün arkasına, "Bilinen açık işler"in önüne)
- Modify: `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` (§Faz 6 başlığı ve maddesi)

- [ ] **Step 1: `docs/deployment.md` bölümü**

```markdown
## Önbellek (Faz 6)

Uygulama bazı sık okunan, az değişen bilgileri kendi belleğinde tutar (Redis yok, tek sunucu).

| Önbellek | Ne tutar | Ne zaman tazelenir |
|---|---|---|
| `tenantPlanCode` | Kliniğin paket kodu | Admin aboneliği değiştirince hemen; aksi halde en geç 5 dk |
| `planByCode` | Paketin açık modülleri ve aylık kotaları | Admin paket oluşturur/günceller/silerse hemen; aksi halde en geç 5 dk |
| `tarbilDiseases` | TARBİL hastalık listesi | Uygulama yeniden başlayınca (liste yalnızca yeni sürümle değişir) |

- Paket bilgisi **admin paneli dışından** (doğrudan veritabanında) değiştirilirse, yeni değerin kliniklere yansıması en fazla 5 dakika sürer. Hemen gerekiyorsa `sudo systemctl restart vetly-backend`.
- Önbellekte kişisel veri (hasta, sahip, personel, telefon, TCKN) tutulmaz.
- Metrikler (8081, Prometheus): `cache_gets_total{cache="tenantPlanCode",result="hit"|"miss"}`, `cache_size`, `cache_evictions_total`.
  Örnek: `curl -s localhost:8081/actuator/prometheus | grep '^cache_gets_total'`
- Ana sayfa özetleri (gelir, bugünkü satış, yeni hasta, randevu) önbelleğe alınmaz; her açılışta veritabanında toplanır. Ay ve gün sınırları İstanbul saatine göredir.
```

- [ ] **Step 2: Yol haritası**

`## Faz 6 — Caching ← **SIRADAKİ**` başlığı `## Faz 6 — Caching ✅` olur. Maddelerin altına şu satır eklenir:

```markdown
- ✅ 2026-10-18: kapsam "ölç-düzelt": paket kodu / paket / TARBİL listesi için kiracı-güvenli @Cacheable (commit sonrası temizlik, 5 dk yedek süre); 4 ana sayfa özeti önbelleksiz DB toplama + İstanbul saati (spec docs/superpowers/specs/2026-10-18-onbellek-faz6-design.md). HTTP Cache-Control ve React Query kapsam dışı.
```

`## Faz 7 — API sözleşmesi ve versiyonlama` başlığının sonuna ` ← **SIRADAKİ**` eklenir.

- [ ] **Step 3: Tam backend test takımı**

Run: `cd backend && mvn -q test`
Expected: Tüm testler yeşil. Önceki toplam 825, buna bu planın eklediği testler eklenir: CacheWiringTest 5, PlanLookupCacheTest 7, TarbilDiseaseCacheTest 1, GetRevenueSummaryUseCaseTest 1, DashboardSummaryQueriesTest 3, GetPatientGrowthSummaryUseCaseTest 1, GetAppointmentActivitySummaryUseCaseTest 2. Toplam ≈ 845, 0 failure, 0 error.

- [ ] **Step 4: Bağımlılık ağacı kontrolü**

Run: `cd backend && mvn -q dependency:tree -Dincludes=com.github.ben-manes.caffeine,org.springframework:spring-context-support`
Expected: Tek bir `caffeine` sürümü (Boot BOM). `spring-context-support` 6.2.19.
