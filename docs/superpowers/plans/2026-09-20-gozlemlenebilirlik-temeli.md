# Gözlemlenebilirlik Temeli Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Her isteğe bir `requestId` atayıp (kimlik doğrulanırsa `tenantId` ile birlikte) konsol loglarına ekleyerek log satırlarını izlenebilir kılmak, prod'da gereksiz `debug` log seviyesini kapatmak, ve Actuator'a metrik/Prometheus uç noktalarını açmak.

**Architecture:** `platform/web` paketine, güvenlik filtre zincirinin en başında çalışan yeni bir `RequestIdFilter` eklenir — her isteğe bir `requestId` üretip SLF4J MDC'ye yazar, istek bitince temizler. `JwtAuthenticationFilter`, zaten yaptığı `TenantContext.set(...)` çağrısının yanına `tenantId`'yi de MDC'ye yazar. Konsol log pattern'i bu iki MDC anahtarını gösterecek şekilde güncellenir. `application-prod.yml`'e `com.vetos` paketi için `info` log seviyesi override'ı eklenir. Actuator'a `micrometer-registry-prometheus` eklenir, `metrics`/`prometheus` uç noktaları açılır — dashboard/alarm kurulumu bu turun kapsamında değil.

**Tech Stack:** SLF4J MDC, Spring Security filter chain, Logback pattern, Micrometer/Prometheus.

**Spec:** `docs/superpowers/specs/2026-09-19-gozlemlenebilirlik-temeli-design.md`

## Global Constraints

- `RequestIdFilter`, güvenlik zincirinin **en başında** çalışmalı — `RateLimitFilter`'dan (F turunda eklendi) ve `JwtAuthenticationFilter`'dan önce. Kimlik doğrulanmamış istekler (başarısız login denemeleri, rate-limit'e takılan istekler, herkese açık uç noktalar) dahil HER istek bir `requestId` almalı.
- **Filtre kayıt sırası kritik** (F turunda keşfedilen Spring Security `FilterOrderRegistration` kısıtlaması geçerli): `addFilterBefore(filter, anchorClass)`, `anchorClass`'ın DAHA ÖNCE bir `addFilterBefore/After/At` çağrısıyla "bilinen" hale gelmiş olmasını gerektirir. Mevcut kayıt sırası (bkz. `SecurityConfig.java`): önce `JwtAuthenticationFilter` (`UsernamePasswordAuthenticationFilter.class`'a ankorlanır), sonra `RateLimitFilter` (`JwtAuthenticationFilter.class`'a ankorlanır). `RequestIdFilter`'ı ÜÇÜNCÜ sırada, `RateLimitFilter.class`'a ankorlayarak eklemek gerekir — bu, çalışma zamanında `RequestIdFilter → RateLimitFilter → JwtAuthenticationFilter → UsernamePasswordAuthenticationFilter` sırasını üretir (istenen budur: `RequestIdFilter` en dışta/en önce çalışır).
- MDC anahtarları: `"requestId"` ve `"tenantId"` (birebir bu string'ler, log pattern'i bunlara referans veriyor).
- Log pattern'indeki varsayılanlar (`%X{requestId:-etc.}/%X{tenantId:-sistem}`) MDC boşken (örn. `@Scheduled` işler) anlamlı bir işaretleyici gösterir — bu davranış korunmalı.
- Bu kod tabanında pure Spring config/wiring/YAML değişiklikleri için ayrı birim test yazılmaz (bkz. Actuator eklemesinin kendisinin test edilmemesi, CI/CD planı) — Task 2 ve Task 3 bu kurala uyar, doğrulama manuel/derleme kontrolüyle yapılır.
- Her görev sonunda `cd backend && ./mvnw test` çalıştırılır, `ApplicationModulesTest` dahil yeşil kalmalı.

---

## Task 1: İzlenebilir Loglama — `RequestIdFilter` + MDC

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/web/RequestIdFilter.java`
- Modify: `backend/src/main/java/com/vetos/platform/security/SecurityConfig.java`
- Modify: `backend/src/main/java/com/vetos/platform/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/vetos/platform/web/RequestIdFilterTest.java`
- Test: `backend/src/test/java/com/vetos/platform/security/JwtAuthenticationFilterTest.java`

**Interfaces:**
- Produces: `RequestIdFilter` (public, `OncePerRequestFilter`) — `SecurityConfig` `new`'ler, Spring bean değil (aynı desen: `RateLimitFilter`, `JwtAuthenticationFilter`).

- [ ] **Step 1: `RequestIdFilterTest`'i yaz (önce başarısız olacak şekilde)**

`backend/src/test/java/com/vetos/platform/web/RequestIdFilterTest.java`:
```java
package com.vetos.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RequestIdFilterTest {

    private final RequestIdFilter filter = new RequestIdFilter();

    @Test
    void should_putRequestIdInMdc_duringFilterChain_and_clearAfterwards() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> seenDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenDuringChain.set(MDC.get("requestId"));

        filter.doFilter(request, response, chain);

        assertThat(seenDuringChain.get()).isNotNull().isNotBlank();
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void should_generateDifferentRequestId_forEachRequest() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        AtomicReference<String> first = new AtomicReference<>();
        AtomicReference<String> second = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> first.set(MDC.get("requestId")));
        filter.doFilter(request, response, (req, res) -> second.set(MDC.get("requestId")));

        assertThat(first.get()).isNotEqualTo(second.get());
    }

    @Test
    void should_clearMdc_evenWhenChainThrows() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = (req, res) -> { throw new ServletException("boom"); };

        try {
            filter.doFilter(request, response, chain);
        } catch (ServletException | IOException ignored) {
            // beklenen
        }

        assertThat(MDC.get("requestId")).isNull();
    }
}
```

- [ ] **Step 2: Testi çalıştır, derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=RequestIdFilterTest`
Expected: derleme hatası — `RequestIdFilter` sınıfı henüz yok.

- [ ] **Step 3: `RequestIdFilter`'ı oluştur**

`backend/src/main/java/com/vetos/platform/web/RequestIdFilter.java`:
```java
package com.vetos.platform.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Guvenlik zincirinin EN BASINDA calisir (kimlik dogrulamadan once) --
 * kimlik dogrulanmamis istekler (basarisiz login denemeleri, herkese acik
 * uc noktalar, rate-limit'e takilan istekler) de dahil HER istek bir
 * requestId alir. JwtAuthenticationFilter (bu filtreden SONRA calisir)
 * kimlik dogrulanirsa ayrica tenantId'yi MDC'ye ekler.
 */
public class RequestIdFilter extends OncePerRequestFilter {
    private static final String MDC_KEY = "requestId";

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        MDC.put(MDC_KEY, UUID.randomUUID().toString());
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }
}
```

- [ ] **Step 4: Testi tekrar çalıştır, geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=RequestIdFilterTest`
Expected: 3 test PASS.

- [ ] **Step 5: `SecurityConfig`'e filtreyi ÜÇÜNCÜ sırada ekle**

`backend/src/main/java/com/vetos/platform/security/SecurityConfig.java` — importlara ekle:
```java
import com.vetos.platform.web.RequestIdFilter;
```
`securityFilterChain` metodundaki mevcut iki `addFilterBefore` satırından SONRA (Global Constraints'teki sıralama gerekçesiyle — `RateLimitFilter.class` bu noktada zaten "bilinen"), üçüncü bir çağrı ekle:
```java
.addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
.addFilterBefore(new RateLimitFilter(), JwtAuthenticationFilter.class)
.addFilterBefore(new RequestIdFilter(), RateLimitFilter.class);
```
(Mevcut yorum bloğu — "RateLimitFilter, JwtAuthenticationFilter'dan SONRA eklenmeli..." — olduğu gibi kalır, üstüne yeni bir açıklama eklenmez; sıralama kuralı zaten anlatılmış, üçüncü satır aynı mantığın devamı.)

- [ ] **Step 6: `JwtAuthenticationFilterTest`'i yaz (önce başarısız olacak şekilde)**

`backend/src/test/java/com/vetos/platform/security/JwtAuthenticationFilterTest.java`:
```java
package com.vetos.platform.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JwtAuthenticationFilterTest {

    private static final String TEST_SECRET = "test-secret-key-must-be-at-least-32-bytes-long!!";
    private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(TEST_SECRET, 480);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenProvider);

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        MDC.clear();
    }

    @Test
    void should_putTenantIdInMdc_duringFilterChain_when_tokenValid() throws Exception {
        UUID tenantId = UUID.randomUUID();
        String token = jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), "ADMIN");
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        AtomicReference<String> seenDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenDuringChain.set(MDC.get("tenantId"));

        filter.doFilter(request, response, chain);

        assertThat(seenDuringChain.get()).isEqualTo(tenantId.toString());
        assertThat(MDC.get("tenantId")).isNull();
    }

    @Test
    void should_notPutTenantIdInMdc_when_noToken() throws Exception {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(request.getHeader("Authorization")).thenReturn(null);
        AtomicReference<String> seenDuringChain = new AtomicReference<>();
        FilterChain chain = (req, res) -> seenDuringChain.set(MDC.get("tenantId"));

        filter.doFilter(request, response, chain);

        assertThat(seenDuringChain.get()).isNull();
    }
}
```

- [ ] **Step 7: Testi çalıştır, derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=JwtAuthenticationFilterTest`
Expected: `should_putTenantIdInMdc_duringFilterChain_when_tokenValid` FAIL (MDC'ye henüz `tenantId` yazılmıyor, `seenDuringChain.get()` null döner).

- [ ] **Step 8: `JwtAuthenticationFilter`'a MDC yazımını ekle**

`backend/src/main/java/com/vetos/platform/security/JwtAuthenticationFilter.java` — importlara ekle:
```java
import org.slf4j.MDC;
```
`TenantContext.set(principal.tenantId());` satırının hemen altına ekle:
```java
TenantContext.set(principal.tenantId());
MDC.put("tenantId", principal.tenantId().toString());
```
Mevcut `finally` bloğundaki `TenantContext.clear();` satırının hemen altına ekle:
```java
} finally {
    TenantContext.clear();
    MDC.remove("tenantId");
    SecurityContextHolder.clearContext();
}
```

- [ ] **Step 9: Testi tekrar çalıştır, geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=JwtAuthenticationFilterTest`
Expected: 2 test PASS.

- [ ] **Step 10: `application.yml`'e konsol log pattern'ini ekle**

`backend/src/main/resources/application.yml` — dosyanın sonundaki `logging:` bloğunu değiştir:
```yaml
logging:
  level:
    com.vetos: debug
  pattern:
    console: "%d{HH:mm:ss.SSS} %-5level [%X{requestId:-etc.}/%X{tenantId:-sistem}] %logger{36} - %msg%n"
```
(Sadece `pattern.console` alt anahtarı yeni; `level.com.vetos: debug` mevcut haliyle kalır — bu, dev/yerel için hâlâ doğru varsayılan, Task 2 sadece prod profilini override eder.)

- [ ] **Step 11: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS, `ApplicationModulesTest` dahil.

- [ ] **Step 12: Uç noktayı yerelde manuel doğrula**

Backend'i başlat: `cd backend && ./mvnw spring-boot:run` (arka planda)
Başka bir terminalde: `curl -s http://localhost:8080/actuator/health`
Backend konsol loğunda bu isteğe ait satırın `[<uuid>/sistem]` formatında bir `requestId` içerdiğini gözle doğrula (henüz kimlik doğrulanmadığı için `tenantId` yerine `sistem` görünmeli).
Backend'i durdur.

- [ ] **Step 13: Commit**

```bash
git add backend/src/main/java/com/vetos/platform/web/RequestIdFilter.java \
  backend/src/main/java/com/vetos/platform/security/SecurityConfig.java \
  backend/src/main/java/com/vetos/platform/security/JwtAuthenticationFilter.java \
  backend/src/main/resources/application.yml \
  backend/src/test/java/com/vetos/platform/web/RequestIdFilterTest.java \
  backend/src/test/java/com/vetos/platform/security/JwtAuthenticationFilterTest.java
git commit -m "feat: requestId/tenantId ile izlenebilir loglama ekle"
```

---

## Task 2: Prod Log Seviyesi

**Files:**
- Modify: `backend/src/main/resources/application-prod.yml`

**Interfaces:** Yok — pure config değişikliği.

- [ ] **Step 1: `application-prod.yml`'e log seviyesi override'ı ekle**

`backend/src/main/resources/application-prod.yml` — dosyanın sonuna (mevcut `app:` bloğundan sonra) ekle:
```yaml

logging:
  level:
    com.vetos: info
```

- [ ] **Step 2: Derlemenin bozulmadığını doğrula**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: hatasız (YAML sözdizimi geçerli).

- [ ] **Step 3: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/application-prod.yml
git commit -m "fix: prod profilinde debug log seviyesini info'ya indir"
```

---

## Task 3: Metrik Uç Noktaları

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`

**Interfaces:** Yok — pure config/bağımlılık değişikliği.

- [ ] **Step 1: `pom.xml`'e Prometheus registry bağımlılığını ekle**

`backend/pom.xml` — `spring-boot-starter-actuator` bağımlılığından hemen sonra (satır 56-57'den sonra, `bucket4j-core` bloğundan önce) ekle:
```xml
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
        </dependency>
```
(Versiyon belirtilmiyor — `spring-boot-starter-parent` BOM'u zaten yönetiyor.)

- [ ] **Step 2: Derlemenin bozulmadığını doğrula**

Run: `cd backend && ./mvnw -q -DskipTests compile`
Expected: hatasız derleme (bağımlılık Maven Central'dan çekilir).

- [ ] **Step 3: `application.yml`'de açılan uç nokta listesini genişlet**

`backend/src/main/resources/application.yml` — mevcut `management` bloğunu değiştir:
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: never
```

- [ ] **Step 4: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 5: Uç noktaları yerelde manuel doğrula**

Backend'i başlat: `cd backend && ./mvnw spring-boot:run` (arka planda)
Başka bir terminalde: `curl -s http://localhost:8080/actuator/prometheus | head -5`
Expected: Prometheus text-format metrik satırları (`# HELP ...`, `# TYPE ...` ile başlayan).
`curl -s http://localhost:8080/actuator/metrics` — Expected: mevcut metrik isimlerinin bir JSON listesi.
Backend'i durdur.

- [ ] **Step 6: Commit**

```bash
git add backend/pom.xml backend/src/main/resources/application.yml
git commit -m "feat: Actuator metrics/prometheus ucnoktalarini ac"
```

---

## Self-Review Notları (plan yazarı tarafından, kaydedilmeden önce)

- **Spec kapsaması:** §3 (izlenebilir loglama, RequestIdFilter + MDC + pattern) → Task 1. §4 (prod log seviyesi) → Task 2. §5 (metrik uç noktaları) → Task 3. §6 (test stratejisi) Task 1'in test adımlarına dağıtıldı (RequestIdFilter + JwtAuthenticationFilter testleri, manuel doğrulama); Task 2/3 spec'in "pure config" kuralına göre test'siz. §7 açık soru yok.
- **Spec'ten sapma (bilinçli, gerekçeli):** Spec'in verdiği örnek kod (`RequestIdFilter`'ı sadece `JwtAuthenticationFilter.class`'a ankorlamak) F turundan (Güvenlik Sertleştirme, bu spec'ten SONRA implemente edildi) önce yazılmış — `RateLimitFilter` o zaman yoktu. Bu plan, spec'in `RequestIdFilter` sınıfının kendi kodunu (satır satır aynı) korurken, `SecurityConfig`'e ekleme sırasını güncel duruma (üç filtre, doğru kayıt sırası) uyarladı — spec'in "her istek requestId alır" amacına daha sadık (rate-limit'e takılan istekler de dahil).
- **Tip/imza tutarlılığı:** `RequestIdFilter` parametresiz constructor (Spring bean değil, `RateLimitFilter`/`JwtAuthenticationFilter` ile aynı desen). MDC anahtarları (`"requestId"`, `"tenantId"`) hem üretim kodunda hem testlerde birebir aynı string.
- **Placeholder taraması:** yok, her adımda tam kod var.
