# Faz 2 — Plan Özellik Kısıtlaması Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Admin panelindeki paket "Modül Erişimi" işaretlerini backend'de (uç + otomatik işler) ve ön yüzde (menü, rota, sayfa parçaları) gerçekten uygulamak; modül listesini tek kaynağa indirmek; kısıtlamayı genel bir ayarla kademeli açmak.

**Architecture:** `com.vetos.platform.feature` paketi `Feature` listesini, `FeatureAccessPort` arayüzünü, `@RequiresFeature` etiketini ve her istekte araya giren `FeatureGateInterceptor`'ı barındırır. `platformadmin` içindeki `PlanFeatureAccessAdapter` kiracının plan kodundan paketin işaretlerini okur (TRIAL → hepsi, paket yok → hiçbiri, ayar kapalı → hepsi). Olay dinleyicileri aynı arayüzle sessizce atlar; ön yüz `GET /api/v1/subscriptions/features` ile görünürlüğü ayarlar.

**Tech Stack:** Spring Boot 3.5 / Spring 6.2, Spring Modulith, JUnit 5 + Mockito + AssertJ + MockMvc standalone; React 18 + TypeScript + Vite (birim test yok, `npm run build`).

**Spec:** `docs/superpowers/specs/2026-10-12-plan-ozellik-kisitlamasi-design.md`

## Global Constraints

- **Commit yok, push yok.** Tüm değişiklikler çalışma ağacında (IntelliJ changelist) kalır; görev sonlarında commit adımı yoktur.
- Yerel `main`, branch yok.
- Migration yok: `plan_feature_flags.feature` değerleri (`AI_ASSISTANT, IMAGING, LAB_INTEGRATION, BOARDING, INVENTORY, E_FATURA`) aynen korunur.
- Ayar: `features.enforcement.enabled: ${FEATURES_ENFORCEMENT_ENABLED:false}` — varsayılan **kapalı**; kapalıyken davranış bugünküyle birebir aynı.
- TRIAL plan kodu → tüm özellikler açık. Paket kaydı yok → tüm özellikler kapalı + `log.warn`.
- Kapalı modül: okuma dahil tüm uçlar **403** `FEATURE_NOT_IN_PLAN` ("Bu ozellik planinizda yok").
- Otomatik işler sessizce atlar; istisna: stok kapalıyken stok kalemli reçete → istisna (işlem geri alınır).
- `/api/v1/public/**` ve `/api/v1/platform-admin/**` uçları etiketlenmez.
- Etiket kullanan her modülün `allowedDependencies` listesine `"platform::feature"` eklenir.
- Java yorum/log/hata mesajları ASCII Türkçe; Türkçe etiket metinleri (`Feature.label()`) ve ön yüz metinleri Türkçe karakterli.
- Backend komutları `backend/` içinde `./mvnw -q test -Dtest=<Sinif>`; sonuç `target/surefire-reports/TEST-*.xml`'den okunur. `@SpringBootTest` testleri çalışan docker Postgres'i (5433) ister; geliştirme DB'si :8080 backend ile ortak, bu fazda migration yok.
- Ön yüz doğrulaması: `cd frontend && npm run build`.

## Review Focus

1. Genel ayar kapalıyken (varsayılan) hiçbir klinikte hiçbir uç 403 dönmemeli → Task 2 `should_enableEverything_when_enforcementDisabled` (adapter) + Task 1 kapalı/açık davranışı adapter üzerinden.
2. Kiracının abonelik kaydı bulunamazsa (veri bozukluğu) özellikler kapalı sayılmalı, istek 500 olmamalı → Task 2 `should_disableEverything_when_subscriptionMissing`.
3. Stok kapalıyken stok kalemi İÇERMEYEN reçete normal kaydedilmeli → Task 5 `should_passThrough_when_inventoryDisabledButNoStockItems`.
4. Platform admin uçları (kiracısız) ve public geri dönüş uçları asla etiketlenmemeli → Task 4 `should_notGatePublicOrPlatformAdminEndpoints`.
5. Klinik ön yüzünde özellik listesi yüklenemezse menü gizlenmemeli (hepsi açık varsayılır) → Task 6 `FeatureContext` kodu + elle kontrol listesi madde 5.

---

### Task 1: `platform/feature` çekirdeği ve araya giren kontrol

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/feature/package-info.java`
- Create: `backend/src/main/java/com/vetos/platform/feature/Feature.java`
- Create: `backend/src/main/java/com/vetos/platform/feature/FeatureAccessPort.java`
- Create: `backend/src/main/java/com/vetos/platform/feature/RequiresFeature.java`
- Create: `backend/src/main/java/com/vetos/platform/feature/FeatureNotInPlanForbiddenException.java`
- Create: `backend/src/main/java/com/vetos/platform/feature/FeatureGateInterceptor.java`
- Create: `backend/src/main/java/com/vetos/platform/feature/FeatureWebConfig.java`
- Test: `backend/src/test/java/com/vetos/platform/feature/FeatureGateInterceptorTest.java`

**Interfaces:**
- Produces:
  - `enum Feature { AI_ASSISTANT, IMAGING, LAB_INTEGRATION, BOARDING, INVENTORY, E_FATURA; String label() }`
  - `interface FeatureAccessPort { boolean isEnabled(UUID tenantId, Feature feature); Set<Feature> enabledFeatures(UUID tenantId); boolean enforcementActive(); }`
  - `@RequiresFeature(Feature value)` (TYPE, METHOD; RUNTIME)
  - `class FeatureNotInPlanForbiddenException extends DomainException` — `new FeatureNotInPlanForbiddenException()`
  - `class FeatureGateInterceptor implements HandlerInterceptor` — `new FeatureGateInterceptor(FeatureAccessPort)`, `static Optional<Feature> requiredFeature(HandlerMethod)`

- [ ] **Step 1: Write the failing test**

`backend/src/test/java/com/vetos/platform/feature/FeatureGateInterceptorTest.java`:

```java
package com.vetos.platform.feature;

import com.vetos.platform.tenancy.TenantContext;
import com.vetos.platform.web.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FeatureGateInterceptorTest {

    private static final UUID TENANT = UUID.randomUUID();

    private final Set<Feature> enabled = EnumSet.noneOf(Feature.class);

    private final FeatureAccessPort featureAccess = new FeatureAccessPort() {
        @Override
        public boolean isEnabled(UUID tenantId, Feature feature) {
            return TENANT.equals(tenantId) && enabled.contains(feature);
        }

        @Override
        public Set<Feature> enabledFeatures(UUID tenantId) {
            return enabled;
        }

        @Override
        public boolean enforcementActive() {
            return true;
        }
    };

    @RestController
    @RequiresFeature(Feature.LAB_INTEGRATION)
    static class LabProbe {
        @GetMapping("/probe/lab")
        String lab() {
            return "ok";
        }

        @RequiresFeature(Feature.INVENTORY)
        @GetMapping("/probe/lab/stock")
        String stock() {
            return "ok";
        }
    }

    @RestController
    static class FreeProbe {
        @GetMapping("/probe/free")
        String free() {
            return "ok";
        }
    }

    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new LabProbe(), new FreeProbe())
        .setControllerAdvice(new GlobalExceptionHandler())
        .addInterceptors(new FeatureGateInterceptor(featureAccess))
        .build();

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void should_allow_when_handlerHasNoAnnotation() throws Exception {
        mockMvc.perform(get("/probe/free")).andExpect(status().isOk());
    }

    @Test
    void should_allow_when_featureEnabled() throws Exception {
        enabled.add(Feature.LAB_INTEGRATION);

        mockMvc.perform(get("/probe/lab")).andExpect(status().isOk());
    }

    @Test
    void should_return403_when_featureDisabled() throws Exception {
        mockMvc.perform(get("/probe/lab"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FEATURE_NOT_IN_PLAN"));
    }

    @Test
    void should_useMethodAnnotation_overClassAnnotation() throws Exception {
        enabled.add(Feature.LAB_INTEGRATION);
        mockMvc.perform(get("/probe/lab/stock")).andExpect(status().isForbidden());

        enabled.clear();
        enabled.add(Feature.INVENTORY);
        mockMvc.perform(get("/probe/lab/stock")).andExpect(status().isOk());
    }

    @Test
    void should_return403_when_noTenantOnAnnotatedEndpoint() throws Exception {
        enabled.addAll(EnumSet.allOf(Feature.class));
        TenantContext.clear();

        mockMvc.perform(get("/probe/lab"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.errorCode").value("FEATURE_NOT_IN_PLAN"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=FeatureGateInterceptorTest`
Expected: COMPILATION ERROR — `cannot find symbol: class Feature` (ve diğer yeni tipler)

- [ ] **Step 3: Write minimal implementation**

`package-info.java`:

```java
@org.springframework.modulith.NamedInterface("feature")
package com.vetos.platform.feature;
```

`Feature.java`:

```java
package com.vetos.platform.feature;

/**
 * Bir paketin (Plan) acabildigi moduller -- TEK KAYNAK. Admin panelindeki "Modul Erisimi"
 * kutulari ve Turkce etiketleri buradan gelir (GET /api/v1/platform-admin/plans/feature-options).
 * Yeni modul: buraya bir deger + o modulun uclarina @RequiresFeature. Degerler
 * plan_feature_flags.feature sutununda STRING olarak saklanir -- yeniden adlandirma migration ister.
 */
public enum Feature {
    AI_ASSISTANT("AI Asistanı (Tanı / Tedavi Önerisi)"),
    IMAGING("Görüntüleme"),
    LAB_INTEGRATION("Laboratuvar Entegrasyonu"),
    BOARDING("Pansiyon / Konaklama"),
    INVENTORY("Stok Yönetimi"),
    E_FATURA("E-Fatura");

    private final String label;

    Feature(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
```

`FeatureAccessPort.java`:

```java
package com.vetos.platform.feature;

import java.util.Set;
import java.util.UUID;

/**
 * "Bu kiracinin paketinde su modul acik mi?" -- uygulamasi platformadmin modulunde
 * (PlanFeatureAccessAdapter). Moduller platformadmin'i dogrudan goremedigi icin arayuz burada.
 */
public interface FeatureAccessPort {

    boolean isEnabled(UUID tenantId, Feature feature);

    Set<Feature> enabledFeatures(UUID tenantId);

    /** features.enforcement.enabled -- kapaliyken her kiraci icin her ozellik acik doner. */
    boolean enforcementActive();
}
```

`RequiresFeature.java`:

```java
package com.vetos.platform.feature;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Controller sinifi veya metodu bu ozelligi ister; metot etiketi sinif etiketinin yerine gecer. */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresFeature {
    Feature value();
}
```

`FeatureNotInPlanForbiddenException.java`:

```java
package com.vetos.platform.feature;

import com.vetos.platform.exception.DomainException;

/** Sinif adi "ForbiddenException" ile bittigi icin GlobalExceptionHandler 403 dondurur. */
public class FeatureNotInPlanForbiddenException extends DomainException {

    public FeatureNotInPlanForbiddenException() {
        super("FEATURE_NOT_IN_PLAN", "Bu ozellik planinizda yok");
    }
}
```

`FeatureGateInterceptor.java`:

```java
package com.vetos.platform.feature;

import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Optional;
import java.util.UUID;

/**
 * @RequiresFeature etiketli uclarda, istegin kiracisinin paketinde ozellik kapaliysa
 * istegi controller'a ulasmadan reddeder (403 FEATURE_NOT_IN_PLAN). Etiketli uca
 * kiraci bilgisi olmadan gelinirse de reddeder -- emin olunamayan durumda acmak yerine kapat.
 * TenantContext, guvenlik filtrelerinde (JWT / eklenti anahtari) kurulur; interceptor
 * DispatcherServlet icinde, yani o filtrelerin icinde calisir.
 */
public class FeatureGateInterceptor implements HandlerInterceptor {

    private final FeatureAccessPort featureAccess;

    public FeatureGateInterceptor(FeatureAccessPort featureAccess) {
        this.featureAccess = featureAccess;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        Optional<Feature> required = requiredFeature(handlerMethod);
        if (required.isEmpty()) {
            return true;
        }
        UUID tenantId = TenantContext.currentOrNull();
        if (tenantId == null || !featureAccess.isEnabled(tenantId, required.get())) {
            throw new FeatureNotInPlanForbiddenException();
        }
        return true;
    }

    public static Optional<Feature> requiredFeature(HandlerMethod handlerMethod) {
        RequiresFeature onMethod = handlerMethod.getMethodAnnotation(RequiresFeature.class);
        if (onMethod != null) {
            return Optional.of(onMethod.value());
        }
        RequiresFeature onClass = AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getBeanType(), RequiresFeature.class);
        return Optional.ofNullable(onClass).map(RequiresFeature::value);
    }
}
```

`FeatureWebConfig.java`:

```java
package com.vetos.platform.feature;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
class FeatureWebConfig implements WebMvcConfigurer {

    private final FeatureAccessPort featureAccess;

    FeatureWebConfig(FeatureAccessPort featureAccess) {
        this.featureAccess = featureAccess;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new FeatureGateInterceptor(featureAccess));
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q test -Dtest=FeatureGateInterceptorTest`
Expected: PASS (5 test). (Not: `FeatureWebConfig` bu testte yüklenmez; `FeatureAccessPort` bean'i Task 2'de gelir — Task 2 bitene kadar `@SpringBootTest` testleri bean eksikliğiyle kırılır, Task 1–2 arasında tam suite çalıştırma.)

---

### Task 2: Plan işaretlerini okuyan adapter, `PlanFeatureFlag` → `Feature` taşıma, genel ayar

**Files:**
- Delete: `backend/src/main/java/com/vetos/modules/platformadmin/domain/PlanFeatureFlag.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/domain/Plan.java` (import + `Set<PlanFeatureFlag>` iki yerde + javadoc)
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/dto/PlanResponse.java`, `.../api/dto/UpdatePlanRequest.java`, `.../application/dto/UpdatePlanCommand.java`
- Modify: `backend/src/test/java/com/vetos/modules/platformadmin/domain/PlanTest.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/package-info.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanFeatureAccessAdapter.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanFeatureAccessAdapterTest.java`

**Interfaces:**
- Consumes: `Feature`, `FeatureAccessPort` (Task 1); `PlanRepository.findByCode(String): Optional<Plan>` (mevcut).
- Produces: `TenantAdminPort.findPlanCode(UUID tenantId): Optional<String>`; `PlanFeatureAccessAdapter` (tek `FeatureAccessPort` bean'i); `Plan.getEnabledFeatures(): Set<Feature>`.

- [ ] **Step 1: Write the failing test**

`PlanFeatureAccessAdapterTest.java`:

```java
package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.feature.Feature;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanFeatureAccessAdapterTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private TenantAdminPort tenantAdminPort;
    @Mock private PlanRepository planRepository;

    private PlanFeatureAccessAdapter adapter(boolean enforcementEnabled) {
        return new PlanFeatureAccessAdapter(tenantAdminPort, planRepository, enforcementEnabled);
    }

    private Plan planWith(Set<Feature> features) {
        Plan plan = Plan.create("PRO", "Pro", new BigDecimal("1000"));
        plan.updateDetails("Pro", new BigDecimal("1000"), null, null, null, null, List.of(), features);
        return plan;
    }

    @Test
    void should_enableEverything_when_enforcementDisabled() {
        PlanFeatureAccessAdapter adapter = adapter(false);

        assertThat(adapter.enabledFeatures(TENANT)).isEqualTo(EnumSet.allOf(Feature.class));
        assertThat(adapter.isEnabled(TENANT, Feature.E_FATURA)).isTrue();
        assertThat(adapter.enforcementActive()).isFalse();
        verifyNoInteractions(tenantAdminPort, planRepository);
    }

    @Test
    void should_enableEverything_when_trialPlan() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("TRIAL"));

        assertThat(adapter(true).enabledFeatures(TENANT)).isEqualTo(EnumSet.allOf(Feature.class));
        verifyNoInteractions(planRepository);
    }

    @Test
    void should_returnPlanFlags_when_planExists() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("PRO"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(planWith(Set.of(Feature.LAB_INTEGRATION, Feature.IMAGING))));
        PlanFeatureAccessAdapter adapter = adapter(true);

        assertThat(adapter.enabledFeatures(TENANT)).containsExactlyInAnyOrder(Feature.LAB_INTEGRATION, Feature.IMAGING);
        assertThat(adapter.isEnabled(TENANT, Feature.LAB_INTEGRATION)).isTrue();
        assertThat(adapter.isEnabled(TENANT, Feature.AI_ASSISTANT)).isFalse();
        assertThat(adapter.enforcementActive()).isTrue();
    }

    @Test
    void should_disableEverything_when_planRowMissing() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("SILINMIS"));
        when(planRepository.findByCode("SILINMIS")).thenReturn(Optional.empty());

        assertThat(adapter(true).enabledFeatures(TENANT)).isEmpty();
    }

    @Test
    void should_disableEverything_when_subscriptionMissing() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.empty());

        assertThat(adapter(true).enabledFeatures(TENANT)).isEmpty();
        verifyNoInteractions(planRepository);
    }
}
```

`PlanTest.java`: `PlanFeatureFlag` geçen her yeri `Feature` yap, importu `import com.vetos.platform.feature.Feature;` ekle (aynı paketteki `PlanFeatureFlag` importsuz kullanılıyordu):

```bash
cd backend && sed -i 's/PlanFeatureFlag\./Feature./g' src/test/java/com/vetos/modules/platformadmin/domain/PlanTest.java
```

ve `PlanTest.java`'nın import bloğuna `import com.vetos.platform.feature.Feature;` satırını ekle.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest='PlanFeatureAccessAdapterTest,PlanTest'`
Expected: COMPILATION ERROR — `cannot find symbol: class PlanFeatureAccessAdapter` ve `PlanTest`'te `Feature` ile `Set<PlanFeatureFlag>` uyuşmazlığı (`incompatible types`).

- [ ] **Step 3: Write minimal implementation**

1) `PlanFeatureFlag` taşıma:

```bash
cd backend && rm src/main/java/com/vetos/modules/platformadmin/domain/PlanFeatureFlag.java
sed -i 's/import com\.vetos\.modules\.platformadmin\.domain\.PlanFeatureFlag;/import com.vetos.platform.feature.Feature;/; s/PlanFeatureFlag/Feature/g' \
  src/main/java/com/vetos/modules/platformadmin/api/dto/PlanResponse.java \
  src/main/java/com/vetos/modules/platformadmin/api/dto/UpdatePlanRequest.java \
  src/main/java/com/vetos/modules/platformadmin/application/dto/UpdatePlanCommand.java
```

(`rm` aracı engellenirse dosyayı IDE/Edit ile sil; içeriği yalnızca enum + javadoc'tu.)

`Plan.java`:
- Import bloğuna `import com.vetos.platform.feature.Feature;` ekle.
- `/** Plan.features'tan (serbest pazarlama metni) BAGIMSIZ -- bkz. PlanFeatureFlag javadoc'u. */` → `/** Plan.features'tan (serbest pazarlama metni) BAGIMSIZ: paketin gercekten actigi moduller (bkz. Feature, FeatureAccessPort). */`
- `private Set<PlanFeatureFlag> enabledFeatures` → `private Set<Feature> enabledFeatures`
- `updateDetails(...)` parametresi `Set<PlanFeatureFlag> enabledFeatures` → `Set<Feature> enabledFeatures`

2) `platformadmin/package-info.java` — `"platform::http"` satırını şu hale getir:

```java
        "platform::http", "platform::feature"
```

3) `TenantAdminPort.java` — `findBillingContactPhone` satırından sonra ekle:

```java
    /** Kiracinin abonelikteki plan kodu -- plan ozellik kisitlamasi (FeatureAccessPort) icin. */
    Optional<String> findPlanCode(UUID tenantId);
```

`TenantAdminPortAdapter.java` — `updateSubscription` metodundan önce ekle:

```java
    @Override
    public Optional<String> findPlanCode(UUID tenantId) {
        return subscriptionJpaRepository.findByTenantId(tenantId).map(Subscription::getPlanCode);
    }
```

(`Optional` importu yoksa `import java.util.Optional;` ekle.)

4) `PlanFeatureAccessAdapter.java`:

```java
package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Kiracinin paketinden acik modulleri okur (spec 2026-10-12 plan ozellik kisitlamasi):
 * ayar kapali -> hepsi acik; TRIAL -> hepsi acik; paket bulunursa "Modul Erisimi" isaretleri;
 * abonelik/paket bulunamazsa hepsi kapali (ucretli ozellik yanlislikla acilmasin) + uyari.
 * Onbellek yok: admin isareti degistirdiginde sonraki istek yeni degeri gorur.
 */
@Component
@Slf4j
class PlanFeatureAccessAdapter implements FeatureAccessPort {

    static final String TRIAL_PLAN_CODE = "TRIAL";

    private final TenantAdminPort tenantAdminPort;
    private final PlanRepository planRepository;
    private final boolean enforcementEnabled;

    PlanFeatureAccessAdapter(
        TenantAdminPort tenantAdminPort,
        PlanRepository planRepository,
        @Value("${features.enforcement.enabled:false}") boolean enforcementEnabled
    ) {
        this.tenantAdminPort = tenantAdminPort;
        this.planRepository = planRepository;
        this.enforcementEnabled = enforcementEnabled;
    }

    @Override
    public boolean isEnabled(UUID tenantId, Feature feature) {
        return enabledFeatures(tenantId).contains(feature);
    }

    @Override
    public Set<Feature> enabledFeatures(UUID tenantId) {
        if (!enforcementEnabled) {
            return EnumSet.allOf(Feature.class);
        }
        Optional<String> planCode = tenantAdminPort.findPlanCode(tenantId);
        if (planCode.isEmpty()) {
            log.warn("Abonelik bulunamadi, tum ozellikler kapali: tenantId={}", tenantId);
            return EnumSet.noneOf(Feature.class);
        }
        if (TRIAL_PLAN_CODE.equals(planCode.get())) {
            return EnumSet.allOf(Feature.class);
        }
        return planRepository.findByCode(planCode.get())
            .<Set<Feature>>map(plan -> Set.copyOf(plan.getEnabledFeatures()))
            .orElseGet(() -> {
                log.warn("Plan bulunamadi, tum ozellikler kapali: tenantId={}, planCode={}", tenantId, planCode.get());
                return EnumSet.noneOf(Feature.class);
            });
    }

    @Override
    public boolean enforcementActive() {
        return enforcementEnabled;
    }
}
```

5) `application.yml` — en sona (kök seviyede) ekle:

```yaml

features:
  enforcement:
    # Plan "Modul Erisimi" isaretlerinin kliniklere uygulanmasi. Mevcut paketlerin
    # isaretleri admin panelinden duzenlenmeden ACILMAMALI (bos isaretli paketteki
    # odeme yapan klinikler kilitlenir). Kapaliyken her klinikte her modul acik.
    enabled: ${FEATURES_ENFORCEMENT_ENABLED:false}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='PlanFeatureAccessAdapterTest,PlanTest,UpdatePlanUseCaseTest,FeatureGateInterceptorTest,ApplicationModulesTest'`
Expected: PASS (tümü). `ApplicationModulesTest` kırmızıysa ihlal mesajındaki modülün `allowedDependencies`'ine `"platform::feature"` ekle (Task 4 diğer modülleri ekler; bu noktada yalnız platformadmin kullanıyor).

---

### Task 3: Yeni uçlar — klinik özellik listesi ve admin özellik seçenekleri

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/tenant/application/GetEnabledFeaturesUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/tenant/api/dto/EnabledFeaturesResponse.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/api/SubscriptionsController.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/api/dto/FeatureOptionsResponse.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/PlanController.java`
- Test: `backend/src/test/java/com/vetos/modules/tenant/application/GetEnabledFeaturesUseCaseTest.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/api/dto/FeatureOptionsResponseTest.java`

**Interfaces:**
- Consumes: `FeatureAccessPort`, `Feature` (Task 1).
- Produces:
  - `GET /api/v1/subscriptions/features` → `{ "enforcementActive": boolean, "enabledFeatures": string[] }` (alfabetik), giriş yapmış tüm personel.
  - `GET /api/v1/platform-admin/plans/feature-options` → `{ "enforcementActive": boolean, "options": [{ "code": string, "label": string }] }` (enum sırasıyla).
  - `record GetEnabledFeaturesUseCase.Result(boolean enforcementActive, List<String> enabledFeatures)`

- [ ] **Step 1: Write the failing tests**

`GetEnabledFeaturesUseCaseTest.java`:

```java
package com.vetos.modules.tenant.application;

import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetEnabledFeaturesUseCaseTest {

    @Mock private FeatureAccessPort featureAccessPort;

    @Test
    void should_returnSortedFeatureCodes_and_enforcementFlag() {
        UUID tenantId = UUID.randomUUID();
        when(featureAccessPort.enabledFeatures(tenantId)).thenReturn(Set.of(Feature.LAB_INTEGRATION, Feature.BOARDING));
        when(featureAccessPort.enforcementActive()).thenReturn(true);

        GetEnabledFeaturesUseCase.Result result = new GetEnabledFeaturesUseCase(featureAccessPort).execute(tenantId);

        assertThat(result.enforcementActive()).isTrue();
        assertThat(result.enabledFeatures()).containsExactly("BOARDING", "LAB_INTEGRATION");
    }
}
```

`FeatureOptionsResponseTest.java`:

```java
package com.vetos.modules.platformadmin.api.dto;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FeatureOptionsResponseTest {

    @Test
    void should_listAllFeaturesInEnumOrder_withTurkishLabels() {
        FeatureOptionsResponse response = FeatureOptionsResponse.of(false);

        assertThat(response.enforcementActive()).isFalse();
        assertThat(response.options()).extracting(FeatureOptionsResponse.Option::code)
            .containsExactly("AI_ASSISTANT", "IMAGING", "LAB_INTEGRATION", "BOARDING", "INVENTORY", "E_FATURA");
        assertThat(response.options().get(0).label()).isEqualTo("AI Asistanı (Tanı / Tedavi Önerisi)");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest='GetEnabledFeaturesUseCaseTest,FeatureOptionsResponseTest'`
Expected: COMPILATION ERROR — `cannot find symbol: class GetEnabledFeaturesUseCase` / `FeatureOptionsResponse`

- [ ] **Step 3: Write minimal implementation**

`GetEnabledFeaturesUseCase.java`:

```java
package com.vetos.modules.tenant.application;

import com.vetos.platform.feature.FeatureAccessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/** Klinik on yuzu icin acik moduller -- menu/rota gorunurlugu (asil koruma FeatureGateInterceptor'da). */
@Service
@RequiredArgsConstructor
public class GetEnabledFeaturesUseCase {

    private final FeatureAccessPort featureAccessPort;

    public record Result(boolean enforcementActive, List<String> enabledFeatures) {}

    public Result execute(UUID tenantId) {
        List<String> codes = featureAccessPort.enabledFeatures(tenantId).stream()
            .map(Enum::name)
            .sorted()
            .toList();
        return new Result(featureAccessPort.enforcementActive(), codes);
    }
}
```

`EnabledFeaturesResponse.java`:

```java
package com.vetos.modules.tenant.api.dto;

import com.vetos.modules.tenant.application.GetEnabledFeaturesUseCase;

import java.util.List;

public record EnabledFeaturesResponse(boolean enforcementActive, List<String> enabledFeatures) {

    public static EnabledFeaturesResponse from(GetEnabledFeaturesUseCase.Result result) {
        return new EnabledFeaturesResponse(result.enforcementActive(), result.enabledFeatures());
    }
}
```

`SubscriptionsController.java`:
- Importlar: `import com.vetos.modules.tenant.api.dto.EnabledFeaturesResponse;`, `import com.vetos.modules.tenant.application.GetEnabledFeaturesUseCase;`
- Alan: `private final GetEnabledFeaturesUseCase getEnabledFeaturesUseCase;`
- Metot (`getCurrent`'tan sonra; `@PreAuthorize` YOK — tüm personel menüyü görüyor):

```java
    /** Tum personel: menu/rota gorunurlugu icin kliniğin acik modulleri. */
    @GetMapping("/features")
    public EnabledFeaturesResponse getFeatures() {
        return EnabledFeaturesResponse.from(getEnabledFeaturesUseCase.execute(TenantContext.current()));
    }
```

(Javadoc'u ASCII yaz: `klinigin`.)

`FeatureOptionsResponse.java`:

```java
package com.vetos.modules.platformadmin.api.dto;

import com.vetos.platform.feature.Feature;

import java.util.Arrays;
import java.util.List;

/** Admin "Modul Erisimi" kutulari -- liste ve etiketler tek kaynaktan (Feature). */
public record FeatureOptionsResponse(boolean enforcementActive, List<Option> options) {

    public record Option(String code, String label) {}

    public static FeatureOptionsResponse of(boolean enforcementActive) {
        List<Option> options = Arrays.stream(Feature.values())
            .map(f -> new Option(f.name(), f.label()))
            .toList();
        return new FeatureOptionsResponse(enforcementActive, options);
    }
}
```

`PlanController.java`:
- Importlar: `import com.vetos.modules.platformadmin.api.dto.FeatureOptionsResponse;`, `import com.vetos.platform.feature.FeatureAccessPort;`
- Alan: `private final FeatureAccessPort featureAccessPort;`
- Metot (`list()`'ten sonra):

```java
    @GetMapping("/feature-options")
    public FeatureOptionsResponse featureOptions() {
        return FeatureOptionsResponse.of(featureAccessPort.enforcementActive());
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='GetEnabledFeaturesUseCaseTest,FeatureOptionsResponseTest,ApplicationModulesTest'`
Expected: PASS (tümü)

---

### Task 4: Uçların etiketlenmesi, modül izinleri ve kablolama testi

**Files:**
- Modify (sınıf etiketi `@RequiresFeature(Feature.X)` + importlar `com.vetos.platform.feature.Feature`, `com.vetos.platform.feature.RequiresFeature`):
  - `backend/src/main/java/com/vetos/modules/ai/api/AiController.java` → `AI_ASSISTANT`
  - `backend/src/main/java/com/vetos/modules/imaging/api/ImagingRecordsController.java` → `IMAGING`
  - `backend/src/main/java/com/vetos/modules/lab/api/LabResultsController.java` → `LAB_INTEGRATION`
  - `backend/src/main/java/com/vetos/modules/boarding/api/BoardingRoomsController.java`, `BoardingStaysController.java` → `BOARDING`
  - `backend/src/main/java/com/vetos/modules/inventory/api/InventoryItemsController.java`, `PendingDeductionsController.java` → `INVENTORY`
  - `backend/src/main/java/com/vetos/modules/integration/efatura/api/EInvoiceController.java` → `E_FATURA`
- Modify (metot etiketi `@RequiresFeature(Feature.INVENTORY)`):
  - `backend/src/main/java/com/vetos/modules/integration/tarbil/api/TarbilController.java`: `@GetMapping("/stock-sync")`, `@PostMapping("/stock-sync/{snapshotId}/apply")`, `@PostMapping("/encounters/{encounterId}/deduction")`, `@GetMapping("/encounters/{encounterId}/deduction")` metotları
  - `backend/src/main/java/com/vetos/modules/integration/tarbil/api/TarbilExtensionController.java`: `@PostMapping("/stock-snapshots")`, `@PostMapping("/stock-snapshots/compare")` metotları
  - `backend/src/main/java/com/vetos/modules/billing/api/InvoicesController.java`: `@PostMapping("/quick-sale")` metodu
- Modify (`allowedDependencies`'e `"platform::feature"`): `ai`, `imaging`, `lab`, `boarding`, `inventory`, `integration/efatura`, `integration/tarbil`, `billing` modüllerinin `package-info.java`'sı
- Test: `backend/src/test/java/com/vetos/platform/feature/FeatureGateWiringTest.java`

**Interfaces:**
- Consumes: `FeatureGateInterceptor.requiredFeature(HandlerMethod)` (Task 1), `GET /api/v1/subscriptions/features` (Task 3; kablolama testinde etiketsiz örnek uç).

**Ruling (plan):** Spec 4.3 tablosunda olmayan `POST /api/v1/invoices/quick-sale` INVENTORY ile etiketlenir: hızlı satış (`CompleteQuickSaleUseCase`) doğrudan stok kalemi satar/düşer; stok kapalı klinikte açık kalması "tamamen kapalı" kararını deler. Ön yüzde "Hızlı Satış" düğmesi de gizlenir (Task 6).

- [ ] **Step 1: Write the failing test**

`FeatureGateWiringTest.java`:

```java
package com.vetos.platform.feature;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canli docker-compose Postgres gerekir (diger @SpringBootTest'ler gibi). Hangi ucun hangi
 * ozellige bagli oldugunu gercek handler mapping uzerinden dogrular -- etiket unutulursa kirilir.
 */
@SpringBootTest
class FeatureGateWiringTest {

    private static final Set<String> TARBIL_STOCK_PATTERNS = Set.of(
        "/api/v1/tarbil/stock-sync",
        "/api/v1/tarbil/stock-sync/{snapshotId}/apply",
        "/api/v1/tarbil/encounters/{encounterId}/deduction",
        "/api/v1/tarbil-extension/stock-snapshots",
        "/api/v1/tarbil-extension/stock-snapshots/compare"
    );

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapping;

    private List<Map.Entry<RequestMappingInfo, HandlerMethod>> handlersWhere(Predicate<String> patternMatches) {
        return mapping.getHandlerMethods().entrySet().stream()
            .filter(e -> e.getKey().getPatternValues().stream().anyMatch(patternMatches))
            .toList();
    }

    private static Predicate<String> under(String prefix) {
        return p -> p.equals(prefix) || p.startsWith(prefix + "/");
    }

    private void assertAllRequire(String prefix, Feature expected) {
        var handlers = handlersWhere(under(prefix));
        assertThat(handlers).as(prefix).isNotEmpty();
        handlers.forEach(e -> assertThat(FeatureGateInterceptor.requiredFeature(e.getValue()))
            .as(e.getKey().toString()).contains(expected));
    }

    @Test
    void should_gateWholeModuleControllers() {
        assertAllRequire("/api/v1/ai", Feature.AI_ASSISTANT);
        assertAllRequire("/api/v1/imaging-records", Feature.IMAGING);
        assertAllRequire("/api/v1/lab-results", Feature.LAB_INTEGRATION);
        assertAllRequire("/api/v1/boarding-rooms", Feature.BOARDING);
        assertAllRequire("/api/v1/boarding-stays", Feature.BOARDING);
        assertAllRequire("/api/v1/inventory-items", Feature.INVENTORY);
        assertAllRequire("/api/v1/inventory/pending-deductions", Feature.INVENTORY);
        assertAllRequire("/api/v1/efatura", Feature.E_FATURA);
    }

    @Test
    void should_gateOnlyStockEndpoints_inTarbilAndBilling() {
        assertAllRequire("/api/v1/invoices/quick-sale", Feature.INVENTORY);
        TARBIL_STOCK_PATTERNS.forEach(p -> assertAllRequire(p, Feature.INVENTORY));

        var otherTarbil = handlersWhere(p -> (p.startsWith("/api/v1/tarbil/") || p.startsWith("/api/v1/tarbil-extension/"))
            && !TARBIL_STOCK_PATTERNS.contains(p));
        assertThat(otherTarbil).isNotEmpty();
        otherTarbil.forEach(e -> assertThat(FeatureGateInterceptor.requiredFeature(e.getValue()))
            .as(e.getKey().toString()).isEmpty());
    }

    @Test
    void should_notGatePublicOrPlatformAdminEndpoints() {
        var handlers = handlersWhere(p -> p.startsWith("/api/v1/public/") || p.startsWith("/api/v1/platform-admin/"));
        assertThat(handlers).isNotEmpty();
        handlers.forEach(e -> assertThat(FeatureGateInterceptor.requiredFeature(e.getValue()))
            .as(e.getKey().toString()).isEmpty());
    }

    @Test
    void should_registerFeatureGateInterceptor() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/subscriptions/features");
        ServletRequestPathUtils.parseAndCache(request);

        HandlerExecutionChain chain = mapping.getHandler(request);

        assertThat(chain).isNotNull();
        assertThat(chain.getInterceptorList()).anyMatch(FeatureGateInterceptor.class::isInstance);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=FeatureGateWiringTest`
Expected: FAIL — `should_gateWholeModuleControllers` ve `should_gateOnlyStockEndpoints_inTarbilAndBilling` (`Expecting Optional to contain AI_ASSISTANT but was empty`); diğer ikisi PASS (etiketsizlik + interceptor kaydı Task 1–3'ten).

- [ ] **Step 3: Implement**

Her sınıf etiketi `@RestController`/`@RequestMapping` satırlarının hemen üstüne, örnek (`LabResultsController.java`):

```java
@RequiresFeature(Feature.LAB_INTEGRATION)
@RestController
@RequestMapping("/api/v1/lab-results")
```

Her metot etiketi ilgili `@GetMapping`/`@PostMapping` satırının hemen üstüne, örnek (`TarbilController.java:125`):

```java
    @RequiresFeature(Feature.INVENTORY)
    @GetMapping("/stock-sync")
```

`package-info.java` güncellemeleri — her dosyada `"platform::exception"` (son platform girdisi) sonrasına ekle:

| Dosya | Yeni son satır |
|---|---|
| `ai/package-info.java` | `"modules.encounter::domain", "platform::security", "platform::tenancy", "platform::exception", "platform::feature"` |
| `imaging/package-info.java` | `"platform::security", "platform::tenancy", "platform::exception", "platform::storage", "platform::feature"` |
| `lab/package-info.java` | `"platform::security", "platform::tenancy", "platform::exception", "platform::storage", "platform::feature"` |
| `boarding/package-info.java` | `"platform::security", "platform::tenancy", "platform::event", "platform::exception", "platform::feature"` |
| `inventory/package-info.java` | `"platform::security", "platform::tenancy", "platform::event", "platform::exception", "platform::feature"` |
| `integration/efatura/package-info.java` | `"platform::security", "platform::tenancy", "platform::event", "platform::exception", "platform::feature"` |
| `integration/tarbil/package-info.java` | `"platform::security", "platform::tenancy", "platform::event", "platform::exception", "platform::feature"` |
| `billing/package-info.java` | `"platform::security", "platform::tenancy", "platform::event", "platform::exception", "platform::web", "platform::feature"` |

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='FeatureGateWiringTest,ApplicationModulesTest'`
Expected: PASS (5 test)

---

### Task 5: Otomatik işler (olay dinleyicileri)

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/inventory/infrastructure/event/EncounterFinalizedEventListener.java`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/infrastructure/event/VaccinationStockEventListener.java`
- Modify: `backend/src/main/java/com/vetos/modules/inventory/infrastructure/event/PrescriptionStockEventListener.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/event/InvoiceIssuedEventListener.java`
- Test: `backend/src/test/java/com/vetos/modules/inventory/infrastructure/event/InventoryFeatureListenersTest.java`
- Test: `backend/src/test/java/com/vetos/modules/integration/efatura/infrastructure/event/InvoiceIssuedEventListenerTest.java`

**Interfaces:**
- Consumes: `FeatureAccessPort.isEnabled(UUID, Feature)`, `FeatureNotInPlanForbiddenException` (Task 1). Kiracı: `TenantContext.current()` (inventory dinleyicileri; olaylar kiracı taşımıyor), `event.tenantId()` (efatura).

- [ ] **Step 1: Write the failing tests**

`InventoryFeatureListenersTest.java`:

```java
package com.vetos.modules.inventory.infrastructure.event;

import com.vetos.modules.encounter.domain.event.EncounterFinalizedEvent;
import com.vetos.modules.encounter.domain.event.PrescriptionCancelledEvent;
import com.vetos.modules.encounter.domain.event.PrescriptionIssuedEvent;
import com.vetos.modules.encounter.domain.event.VaccinationCancelledEvent;
import com.vetos.modules.encounter.domain.event.VaccinationRecordedEvent;
import com.vetos.modules.inventory.application.ApplyVaccinationStockUseCase;
import com.vetos.modules.inventory.application.AutoDeductStockForEncounterUseCase;
import com.vetos.modules.inventory.application.PendingDeductionService;
import com.vetos.modules.inventory.domain.InventoryItemRepository;
import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import com.vetos.platform.feature.FeatureNotInPlanForbiddenException;
import com.vetos.platform.tenancy.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryFeatureListenersTest {

    private static final UUID TENANT = UUID.randomUUID();

    @Mock private FeatureAccessPort featureAccess;
    @Mock private AutoDeductStockForEncounterUseCase autoDeduct;
    @Mock private ApplyVaccinationStockUseCase vaccinationStock;
    @Mock private PendingDeductionService pending;
    @Mock private InventoryItemRepository items;

    @BeforeEach
    void setTenant() {
        TenantContext.set(TENANT);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private void inventory(boolean enabled) {
        when(featureAccess.isEnabled(TENANT, Feature.INVENTORY)).thenReturn(enabled);
    }

    @Test
    void should_skipEncounterDeduction_when_inventoryDisabled() {
        inventory(false);
        var event = new EncounterFinalizedEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
            List.of(new EncounterFinalizedEvent.UsedItem(UUID.randomUUID(), 2)));

        new EncounterFinalizedEventListener(autoDeduct, featureAccess).onEncounterFinalized(event);

        verifyNoInteractions(autoDeduct);
    }

    @Test
    void should_deductEncounterStock_when_inventoryEnabled() {
        inventory(true);
        UUID encounterId = UUID.randomUUID();
        var event = new EncounterFinalizedEvent(encounterId, UUID.randomUUID(), UUID.randomUUID(), List.of());

        new EncounterFinalizedEventListener(autoDeduct, featureAccess).onEncounterFinalized(event);

        verify(autoDeduct).execute(eq(encounterId), any());
    }

    @Test
    void should_skipVaccinationStock_when_inventoryDisabled() {
        inventory(false);
        var listener = new VaccinationStockEventListener(vaccinationStock, featureAccess);

        listener.onVaccinationRecorded(new VaccinationRecordedEvent(UUID.randomUUID(), UUID.randomUUID(), "Kuduz",
            LocalDate.now(), UUID.randomUUID(), "LOT1"));
        listener.onVaccinationCancelled(new VaccinationCancelledEvent(UUID.randomUUID(), UUID.randomUUID()));

        verifyNoInteractions(vaccinationStock);
    }

    @Test
    void should_rejectStockPrescription_when_inventoryDisabled() {
        inventory(false);
        var event = new PrescriptionIssuedEvent(UUID.randomUUID(), UUID.randomUUID(), null,
            List.of(new PrescriptionIssuedEvent.StockItem(UUID.randomUUID(), 1, "Metacam")));
        var listener = new PrescriptionStockEventListener(pending, items, featureAccess);

        assertThatThrownBy(() -> listener.onIssued(event)).isInstanceOf(FeatureNotInPlanForbiddenException.class);
        verifyNoInteractions(pending, items);
    }

    @Test
    void should_passThrough_when_inventoryDisabledButNoStockItems() {
        var event = new PrescriptionIssuedEvent(UUID.randomUUID(), UUID.randomUUID(), null, List.of());

        new PrescriptionStockEventListener(pending, items, featureAccess).onIssued(event);

        verifyNoInteractions(pending, items, featureAccess);
    }

    @Test
    void should_skipPrescriptionCancel_when_inventoryDisabled() {
        inventory(false);

        new PrescriptionStockEventListener(pending, items, featureAccess).onCancelled(new PrescriptionCancelledEvent(UUID.randomUUID()));

        verifyNoInteractions(pending);
    }
}
```

`InvoiceIssuedEventListenerTest.java`:

```java
package com.vetos.modules.integration.efatura.infrastructure.event;

import com.vetos.modules.billing.domain.event.InvoiceIssuedEvent;
import com.vetos.modules.integration.efatura.application.QueueEInvoiceSubmissionUseCase;
import com.vetos.platform.feature.Feature;
import com.vetos.platform.feature.FeatureAccessPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InvoiceIssuedEventListenerTest {

    @Mock private QueueEInvoiceSubmissionUseCase queue;
    @Mock private FeatureAccessPort featureAccess;

    private InvoiceIssuedEvent event(UUID tenantId) {
        return new InvoiceIssuedEvent(UUID.randomUUID(), tenantId, UUID.randomUUID(),
            new BigDecimal("120.00"), new BigDecimal("20.00"), Instant.now());
    }

    @Test
    void should_notQueue_when_efaturaDisabled() {
        UUID tenantId = UUID.randomUUID();
        when(featureAccess.isEnabled(tenantId, Feature.E_FATURA)).thenReturn(false);

        new InvoiceIssuedEventListener(queue, featureAccess).onInvoiceIssued(event(tenantId));

        verifyNoInteractions(queue);
    }

    @Test
    void should_queue_when_efaturaEnabled() {
        UUID tenantId = UUID.randomUUID();
        when(featureAccess.isEnabled(tenantId, Feature.E_FATURA)).thenReturn(true);

        new InvoiceIssuedEventListener(queue, featureAccess).onInvoiceIssued(event(tenantId));

        verify(queue).execute(eq(tenantId), any(), any(), any(), any(), any());
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest='InventoryFeatureListenersTest,InvoiceIssuedEventListenerTest'`
Expected: COMPILATION ERROR — dinleyici kurucuları `FeatureAccessPort` parametresi almıyor (`constructor ... cannot be applied to given types`).

- [ ] **Step 3: Implement**

Dört dinleyiciye `private final FeatureAccessPort featureAccess;` alanı ekle (Lombok `@RequiredArgsConstructor` kurucuya son parametre olarak ekler; testler bu sırayı kullanır) ve importlar: `com.vetos.platform.feature.Feature`, `com.vetos.platform.feature.FeatureAccessPort` (+ gereken yerde `FeatureNotInPlanForbiddenException`, `com.vetos.platform.tenancy.TenantContext`, `lombok.extern.slf4j.Slf4j`). Sınıflara `@Slf4j`.

`EncounterFinalizedEventListener.onEncounterFinalized` başına:

```java
        if (!featureAccess.isEnabled(TenantContext.current(), Feature.INVENTORY)) {
            log.info("Stok modulu planda kapali, muayene stok dusumu atlandi: encounterId={}", event.encounterId());
            return;
        }
```

`VaccinationStockEventListener` — iki metodun başına:

```java
        if (!featureAccess.isEnabled(TenantContext.current(), Feature.INVENTORY)) {
            log.info("Stok modulu planda kapali, asi stok islemi atlandi: vaccinationRecordId={}", event.vaccinationRecordId());
            return;
        }
```

`PrescriptionStockEventListener.onIssued` başına (stok kalemi yoksa port hiç çağrılmaz):

```java
        if (event.stockItems().isEmpty()) {
            return;
        }
        if (!featureAccess.isEnabled(TenantContext.current(), Feature.INVENTORY)) {
            // Stok kapaliyken "stoktan ver" kalemi dogrulanamaz -- receteyi geri al (spec 4.4).
            throw new FeatureNotInPlanForbiddenException();
        }
```

`PrescriptionStockEventListener.onCancelled` başına:

```java
        if (!featureAccess.isEnabled(TenantContext.current(), Feature.INVENTORY)) {
            return;
        }
```

`InvoiceIssuedEventListener.onInvoiceIssued` başına:

```java
        if (!featureAccess.isEnabled(event.tenantId(), Feature.E_FATURA)) {
            log.info("E-fatura modulu planda kapali, GIB gonderimi atlandi: invoiceId={}", event.invoiceId());
            return;
        }
```

Sınıf javadoc'larına tek satır: `Plan ozelligi kapaliysa sessizce atlar (spec 2026-10-12 plan ozellik kisitlamasi 4.4).` (Reçete dinleyicisinde: `stok kalemli recete reddedilir`.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='InventoryFeatureListenersTest,InvoiceIssuedEventListenerTest,ApplicationModulesTest'`
Expected: PASS (tümü). MockitoExtension `UnnecessaryStubbingException` verirse ilgili testte gereksiz `inventory(...)` çağrısını kaldır.

---

### Task 6: Klinik ön yüzü

**Files:**
- Modify: `frontend/src/api/subscriptionApi.ts`
- Create: `frontend/src/auth/FeatureContext.tsx`
- Create: `frontend/src/components/FeatureNotInPlanPage.tsx`
- Modify: `frontend/src/main.tsx`, `frontend/src/auth/RequireAuth.tsx`, `frontend/src/App.tsx`
- Modify: `frontend/src/components/layout/navConfig.tsx`, `frontend/src/components/layout/Sidebar.tsx`
- Modify: `frontend/src/pages/settings/SettingsPage.tsx`
- Modify: `frontend/src/pages/encounter/EncounterPage.tsx`, `frontend/src/pages/encounter/PrescriptionCard.tsx`
- Modify: `frontend/src/pages/vaccinations/NewVaccinationPage.tsx`
- Modify: `frontend/src/pages/patients/PatientDetailPage.tsx`
- Modify: `frontend/src/pages/finance/FinancePage.tsx`

**Interfaces:**
- Consumes: `GET /api/v1/subscriptions/features` (Task 3).
- Produces: `type FeatureCode`, `FeatureProvider`, `useFeatures(): { hasFeature(code: FeatureCode): boolean }`; `RequireAuth` yeni `feature?: FeatureCode` prop'u.

**Ruling (plan):** Spec 4.6'daki `<FeatureRoute>` ayrı bileşen yerine mevcut `RequireAuth`'a `feature` prop'u eklenir — rotalar zaten `RequireAuth` ile sarılı, ikinci sarmalayıcı gereksiz; davranış aynı.

- [ ] **Step 1: API ve bağlam**

`subscriptionApi.ts` — `SubscriptionOverview` arayüzünden sonra:

```ts
export interface EnabledFeatures {
  enforcementActive: boolean;
  enabledFeatures: string[];
}
```

`subscriptionApi` nesnesinde `getCurrent` satırından sonra:

```ts
  getFeatures: () => apiClient.get<EnabledFeatures>('/api/v1/subscriptions/features'),
```

`frontend/src/auth/FeatureContext.tsx`:

```tsx
import { createContext, ReactNode, useContext, useEffect, useState } from 'react';
import { subscriptionApi } from '../api/subscriptionApi';
import { useAuth } from './AuthContext';

export type FeatureCode = 'AI_ASSISTANT' | 'IMAGING' | 'LAB_INTEGRATION' | 'BOARDING' | 'INVENTORY' | 'E_FATURA';

interface FeatureContextValue {
  hasFeature: (code: FeatureCode) => boolean;
}

const FeatureContext = createContext<FeatureContextValue>({ hasFeature: () => true });

/**
 * Kliniğin paketinde açık modüller — yalnız görünürlük içindir; asıl koruma backend'de
 * (403 FEATURE_NOT_IN_PLAN). Liste yüklenirken veya yüklenemezse her şey AÇIK varsayılır:
 * modülü yanlışlıkla gizlemek, tıklayınca anlaşılır bir 403 görmekten daha kötüdür.
 */
export function FeatureProvider({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  const [enabled, setEnabled] = useState<Set<string> | null>(null);
  const token = session?.token ?? null;

  useEffect(() => {
    setEnabled(null);
    if (!token) return;
    let cancelled = false;
    subscriptionApi
      .getFeatures()
      .then((r) => {
        if (!cancelled) setEnabled(new Set(r.enabledFeatures));
      })
      .catch(() => {
        if (!cancelled) setEnabled(null);
      });
    return () => {
      cancelled = true;
    };
  }, [token]);

  const hasFeature = (code: FeatureCode) => enabled === null || enabled.has(code);
  return <FeatureContext.Provider value={{ hasFeature }}>{children}</FeatureContext.Provider>;
}

export function useFeatures() {
  return useContext(FeatureContext);
}
```

`main.tsx` — `<AuthProvider>` içini sar:

```tsx
      <AuthProvider>
        <FeatureProvider>
          <App />
        </FeatureProvider>
      </AuthProvider>
```

ve `import { FeatureProvider } from './auth/FeatureContext';` ekle.

- [ ] **Step 2: "Paketinizde yok" sayfası ve rota koruması**

`frontend/src/components/FeatureNotInPlanPage.tsx`:

```tsx
import { Link } from 'react-router-dom';
import { AppShell } from './layout/AppShell';
import { Card } from './ui/Card';

export function FeatureNotInPlanPage() {
  return (
    <AppShell>
      <Card style={{ maxWidth: 520, margin: '48px auto', padding: 24, textAlign: 'center' }}>
        <h2 style={{ marginTop: 0 }}>Bu modül paketinizde yok</h2>
        <p>
          Bu modülü kullanmak için paketinizi yükseltebilirsiniz:{' '}
          <Link to="/ayarlar/abonelik">Ayarlar › Abonelik</Link>
        </p>
      </Card>
    </AppShell>
  );
}
```

(`Card` `...rest` ile `style` geçirir; tip hatası olursa `CardProps`'un `HTMLAttributes<HTMLDivElement>` genişlettiğini kontrol et, yoksa `div` kullan.)

`RequireAuth.tsx` tamamı:

```tsx
import { ReactNode } from 'react';
import { Navigate } from 'react-router-dom';
import { FeatureNotInPlanPage } from '../components/FeatureNotInPlanPage';
import { useAuth } from './AuthContext';
import { FeatureCode, useFeatures } from './FeatureContext';
import { StaffRole } from './session';

interface RequireAuthProps {
  children: ReactNode;
  /** Belirtilmezse tüm oturum açmış roller erişebilir. */
  roles?: StaffRole[];
  /** Belirtilirse kliniğin paketinde bu modül açık olmalı; değilse "paketinizde yok" sayfası. */
  feature?: FeatureCode;
}

export function RequireAuth({ children, roles, feature }: RequireAuthProps) {
  const { session } = useAuth();
  const { hasFeature } = useFeatures();
  if (!session) {
    return <Navigate to="/login" replace />;
  }
  if (roles && !roles.includes(session.role)) {
    return <Navigate to="/panel" replace />;
  }
  if (feature && !hasFeature(feature)) {
    return <FeatureNotInPlanPage />;
  }
  return <>{children}</>;
}
```

`App.tsx` — şu rotaların `<RequireAuth ...>` açılışına `feature` ekle:
- `/laboratuvar` → `<RequireAuth roles={['VET', 'TECHNICIAN', 'ADMIN']} feature="LAB_INTEGRATION">`
- `/goruntuleme` → `<RequireAuth roles={['VET', 'TECHNICIAN', 'ADMIN']} feature="IMAGING">`
- `/konaklama` ve `/konaklama/yeni` → `<RequireAuth feature="BOARDING">`
- `/stok` → `<RequireAuth feature="INVENTORY">`

- [ ] **Step 3: Menü ve ayar sekmeleri**

`navConfig.tsx`:
- `import { FeatureCode } from '../../auth/FeatureContext';`
- `NavItemConfig`'e: `/** Belirtilirse yalnız kliniğin paketinde bu modül açıksa görünür. */ feature?: FeatureCode;`
- Öğelere ekle: Laboratuvar `feature: 'LAB_INTEGRATION',`, Görüntüleme `feature: 'IMAGING',`, Konaklama `feature: 'BOARDING',`, Stok `feature: 'INVENTORY',` (her birinde `path` satırından sonra).

`Sidebar.tsx`:
- `import { useFeatures } from '../../auth/FeatureContext';`
- `const { session } = useAuth();` satırından sonra `const { hasFeature } = useFeatures();`
- filtre:

```tsx
  const visibleNavItems = NAV_ITEMS.filter(
    (item) =>
      (!item.roles || (session && item.roles.includes(session.role))) && (!item.feature || hasFeature(item.feature)),
  );
```

`SettingsPage.tsx`:
- `import { FeatureCode, useFeatures } from '../../auth/FeatureContext';`
- `SettingsTabConfig`'e `feature?: FeatureCode;`
- `{ path: 'ai-merkezi', label: 'AI Merkezi', feature: 'AI_ASSISTANT' },` ve `{ path: 'e-fatura', label: 'e-Fatura', roles: ['ADMIN'], feature: 'E_FATURA' },`
- Bileşende: `const { hasFeature } = useFeatures();` ve `const hasAccess = (t: SettingsTabConfig) => hasRoles(t.roles) && (!t.feature || hasFeature(t.feature));`
- `const notInPlan = <div className={styles.errorBanner}>Bu modül paketinizde yok</div>;`
- Rotalar:

```tsx
        <Route path="ai-merkezi" element={hasFeature('AI_ASSISTANT') ? <AiCenterPanel /> : notInPlan} />
```

```tsx
        <Route path="e-fatura" element={!isAdmin ? forbidden : hasFeature('E_FATURA') ? <EfaturaPanel /> : notInPlan} />
```

- [ ] **Step 4: Sayfa içi parçalar**

`EncounterPage.tsx`:
- `import { useFeatures } from '../../auth/FeatureContext';`; bileşen başında `const { hasFeature } = useFeatures();`, `const canUseAi = hasFeature('AI_ASSISTANT');`, `const canUseStock = hasFeature('INVENTORY');`
- Başlığı `✦ AI Tanı Desteği`, `✦ AI Tedavi Önerisi` ve `✦ AI Scribe` olan üç `<div className={styles.aiCard}>` bloğunun her birini (açılış `<div className={styles.aiCard}>` satırından eşleşen kapanış `</div>`'ına kadar) `{canUseAi && ( ... )}` ile sar.
- Stok parçaları:

```tsx
          {canUseStock && <MaterialsUsedCard encounterId={encounter.id} readOnly={isReadOnly} />}

          {canUseStock && (encounter.status === 'FINALIZED' || encounter.status === 'AMENDED') && (
            <TarbilDeductionCard encounterId={encounter.id} canAct={session?.role === 'VET' || session?.role === 'ADMIN'} />
          )}
```

`PrescriptionCard.tsx`:
- `import { useFeatures } from '../../auth/FeatureContext';`; bileşen başında `const canUseStock = useFeatures().hasFeature('INVENTORY');`
- Satır ~81: `inventoryApi.list().then(setStockItems).catch(() => setStockItems([]));` → `if (canUseStock) inventoryApi.list().then(setStockItems).catch(() => setStockItems([]));` (efekt bağımlılık dizisine `canUseStock` ekle)
- "Kliniğin stoğundan ver" `<label className={styles.stockToggle}> ... </label>` bloğunu `{canUseStock && ( ... )}` ile sar.

`NewVaccinationPage.tsx`:
- `import { useFeatures } from '../../auth/FeatureContext';`; bileşen başında `const canUseStock = useFeatures().hasFeature('INVENTORY');`
- `<VaccineStockPicker ... />` öğesini `{canUseStock && ( <VaccineStockPicker ... /> )}` ile sar (altındaki `{!stockItem && (...)}` serbest metin girişi stok kapalıyken her zaman görünür).

`PatientDetailPage.tsx`:
- `import { useFeatures } from '../../auth/FeatureContext';`; bileşen başında `const { hasFeature } = useFeatures();`, `const canLab = hasFeature('LAB_INTEGRATION');`, `const canImaging = hasFeature('IMAGING');`
- `load()` içinde: `if (canLab) labApi.list(patientId).then(setLabResults);` ve `if (canImaging) imagingApi.list(patientId).then(setImagingRecords);`
- "Lab Sonuçları" sekme `div`'ini `{canLab && ( ... )}`, "Görüntüleme" sekme `div`'ini `{canImaging && ( ... )}` ile sar.

`FinancePage.tsx`:
- `import { useFeatures } from '../../auth/FeatureContext';`; bileşen başında `const canUseStock = useFeatures().hasFeature('INVENTORY');`
- "Hızlı Satış" `<Button variant="secondary" onClick={() => setQuickSaleOpen(true)}> Hızlı Satış </Button>` öğesini `{canUseStock && ( ... )}` ile sar.

- [ ] **Step 5: Build**

Run: `cd frontend && npm run build`
Expected: `tsc` hatasız, `vite build` "built in" satırı.

---

### Task 7: Admin paneli — modül listesi backend'den, kısıtlama uyarısı

**Files:**
- Modify: `frontend/src/api/platformAdminApi.ts`
- Modify: `frontend/src/pages/platform-admin/PlanManagementPage.tsx`

**Interfaces:**
- Consumes: `GET /api/v1/platform-admin/plans/feature-options` (Task 3).

- [ ] **Step 1: API**

`platformAdminApi.ts`:
- `export type PlanFeatureFlag = ...` satırını sil; `Plan.enabledFeatures` ve `UpdatePlanPayload.enabledFeatures` tiplerini `string[]` yap.
- Ekle:

```ts
export interface FeatureOptions {
  enforcementActive: boolean;
  options: { code: string; label: string }[];
}
```

- `platformAdminApi` nesnesinde `listPlans` satırından sonra:

```ts
  getFeatureOptions: () => platformAdminClient.get<FeatureOptions>('/api/v1/platform-admin/plans/feature-options'),
```

- [ ] **Step 2: Paket ekranı**

`PlanManagementPage.tsx`:
- Import: `import { FeatureOptions, Plan, platformAdminApi } from '../../api/platformAdminApi';`
- `PlanFormState.enabledFeatures: string[];`
- `FEATURE_FLAG_OPTIONS` ve `FEATURE_FLAG_LABELS` sabitlerini sil.
- State: `const [featureOptions, setFeatureOptions] = useState<FeatureOptions | null>(null);`
- `useEffect(load, []);` satırından sonra:

```tsx
  useEffect(() => {
    platformAdminApi
      .getFeatureOptions()
      .then(setFeatureOptions)
      .catch(() => setFeatureOptions(null));
  }, []);
```

- `function toggleFeatureFlag(flag: string) {` (tip değişikliği)
- Başlıktan sonra (`<div className={styles.title}>Plan Kataloğu</div>` altına):

```tsx
      {featureOptions && !featureOptions.enforcementActive && (
        <div className={styles.errorBanner}>
          Modül kısıtlaması henüz aktif değil — "Modül Erişimi" işaretleri şu an kliniklere uygulanmıyor.
        </div>
      )}
```

- "Modül Erişimi" kutuları:

```tsx
              <FieldWrap label="Modül Erişimi (bu plan hangi modülleri açar)">
                {(featureOptions?.options ?? []).map((option) => (
                  <label key={option.code} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, marginBottom: 6 }}>
                    <input
                      type="checkbox"
                      checked={form.enabledFeatures.includes(option.code)}
                      onChange={() => toggleFeatureFlag(option.code)}
                    />
                    {option.label}
                  </label>
                ))}
              </FieldWrap>
```

- [ ] **Step 3: Build**

Run: `cd frontend && npm run build`
Expected: hatasız; `grep -rn "PlanFeatureFlag\|FEATURE_FLAG_" frontend/src` boş.

---

### Task 8: Faz doğrulaması

**Files:** `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md`

- [ ] **Step 1: Tam backend suite**

Run: `cd backend && ./mvnw test`
Expected: `BUILD SUCCESS`, 0 failure/error. Kırmızı varsa testin adını ve nedenini raporla; `@SpringBootTest` bağlam hatası çoğunlukla `FeatureAccessPort` bean'i veya Modulith izinleridir.

- [ ] **Step 2: Ön yüz build**

Run: `cd frontend && npm run build`
Expected: hatasız.

- [ ] **Step 3: Elle kontrol listesi (kullanıcıya)**

Kullanıcı :8080 backend'i yeniden başlattıktan sonra:
1. Ayar kapalıyken (varsayılan): tüm menüler görünür, admin Paket ekranında "Modül kısıtlaması henüz aktif değil" uyarısı var, kutular 6 seçenekle çiziliyor.
2. Admin'de bir paket oluştur (ör. `TEMEL`, yalnız Görüntüleme işaretli), test kliniğinin aboneliğini bu pakete geçir, `FEATURES_ENFORCEMENT_ENABLED=true` ile backend'i başlat.
3. Klinik: menüde Laboratuvar/Konaklama/Stok yok, Görüntüleme var; `/laboratuvar` adresi "Bu modül paketinizde yok" sayfası; muayenede AI kartları, malzeme kartı ve "Kliniğin stoğundan ver" yok; finans'ta "Hızlı Satış" yok.
4. `curl -i -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/lab-results` → `403`, `"errorCode":"FEATURE_NOT_IN_PLAN"`.
5. Ağ bağlantısı kesikken (veya `/subscriptions/features` 500 dönerken) menüler gizlenmez.
6. Admin paketteki Laboratuvar kutusunu işaretler → klinikte sayfa yenilenince Laboratuvar menüsü görünür, uç 200 döner.

- [ ] **Step 4: Yol haritası**

`2026-10-12-teknik-altyapi-yol-haritasi.md`: Faz 2 başlığındaki `← **SIRADAKİ** (önce brainstorming)` → `✅ kod tamamlandı (2026-10-12) — devreye alma: spec §7`; Faz 3 başlığına `← **SIRADAKİ**` ekle. (Commit yok.)
