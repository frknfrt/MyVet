# Faz 4 — Rate Limiting Sertleştirme ve Paket Kotaları Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** IP rate limit'i sınırlı belleğe ve standart 429 yanıtına taşımak, giriş yapmış trafiğe kiracı başına koruma sınırları koymak ve admin panelindeki paketlerden gelen aylık AI / mesaj kotalarını uygulamak.

**Architecture:** Koruma sınırları bellekte (Bucket4j + Caffeine) — `platform.ratelimit` paketi + iki servlet filtresi. Kotalar Postgres'te (`tenant_monthly_usage`, tek SQL komutuyla atomik ayırma) — `platform.quota` portu, `platformadmin` içinde uygulaması; `ai` ve `notification` modülleri portu çağırır. Ekranlar mevcut sayfalara küçük eklemeler.

**Tech Stack:** Spring Boot 3.5.16, Java 21, Spring Modulith, Bucket4j (mevcut), Caffeine (yeni, Boot BOM), NamedParameterJdbcTemplate, Flyway, React 18 + TypeScript (Vite).

**Spec:** `docs/superpowers/specs/2026-10-15-rate-limit-kota-faz4-design.md`

## Global Constraints

- **Commit YOK, push YOK.** Her görevin "commit" adımı yoktur; değişiklikler working tree'de (IntelliJ changelist) kalır. `git add`/`git commit`/`git stash`/`git checkout -- <file>` çalıştırma.
- Doğrudan `main` üzerinde çalışılır; branch/worktree açılmaz.
- Testler `backend/` içinden: `./mvnw -q test -Dtest=<TestAdi>`. `@SpringBootTest` testleri canlı docker Postgres ister (`backend/` içinde `docker compose up -d`, port 5433). Testcontainers KULLANILMAZ.
- Kod yorumları ASCII Türkçe (mevcut stil: "kiraci", "sinir"); kullanıcıya giden mesajlar Türkçe karakterli tam cümle.
- Yeni `platform` alt paketleri `package-info.java` içinde `@org.springframework.modulith.NamedInterface("<ad>")` taşır. Modül `allowedDependencies` listelerine yalnızca gereken `platform::<ad>` eklenir; `tenant` modülünün listesi yok — dokunma.
- Koruma sınırı değerleri (spec §1.1): `tenant-general-per-minute: 600`, `tenant-ai-per-minute: 10`, `campaign-recipients-per-hour: 2000`. Kampanya istek başı sınırı `SendCampaignRequest.MAX_RECIPIENTS = 1000` (kodda sabit).
- Mevcut IP limitleri sayı ve davranış olarak aynen kalır (auth/platform-admin auth/extension pair 5 dk'da 10, public dakikada 60).
- TRIAL kotaları: `vetly.quota.trial.ai: 30`, `vetly.quota.trial.message: 100`.
- 429 mesajı: `"Çok fazla istek gönderildi, lütfen {N} saniye sonra tekrar deneyin."`; AI kota mesajı: `"Bu ayki AI hakkınız doldu ({used}/{quota}). Paketinizi yükseltmek için yöneticinize başvurun."`; mesaj kota mesajı: `"Bu ayki mesaj hakkınız yetersiz: {requested} alıcı, kalan {remaining}."`
- `errorCode`'lar: `RATE_LIMITED` (429 + `Retry-After`), `MONTHLY_QUOTA_EXCEEDED` (429, `Retry-After` yok).
- Ay dönemi: Europe/Istanbul takvim ayı, `yyyy-MM`.

## Review Focus

1. **Paketin kota alanı boş (NULL) = sınırsız, 0 değil.** `Optional.map(plan -> plan.getMonthlyAiQuota())` null döndürünce boş Optional'a dönüşüp "plan yok → 0" dalına düşer — sessizce herkesi kilitler. Task 3'te `should_treatNullPlanQuotaAsUnlimited` testi bunu sabitler.
2. **Kova kapasitesinden büyük istek** (ör. yml'de saatlik alıcı 500'e düşürülmüş, kampanya 800 uygun alıcı): Bucket4j bunu asla karşılayamaz; sonsuz/taşan bekleme süresi değil, sonlu `Retry-After` ile 429 dönmeli. Task 1'de `should_rejectWithFiniteRetry_when_tokensExceedCapacity`.
3. **Ay sınırı UTC değil İstanbul saatine göre:** 31 Ekim 21:30 UTC = 1 Kasım 00:30 İstanbul → dönem `2026-11`. Task 3'te `should_useIstanbulCalendarMonth`.
4. **AI adaptörü hatayı yutup `modelConnected=false` boş taslak döndürünce hak iade edilmeli** (istisna fırlamıyor). Task 4'te her use case için `should_release_when_modelNotConnected`.
5. **Kiracısız istekler** (platform admin, anonim, geçersiz token) `TenantRateLimitFilter`'dan hatasız geçmeli; eklenti zinciri genel tavana tabi olmalı. Task 2'de `should_passThrough_when_noTenant` ve wiring testi.

---

### Task 1: IP rate limit sertleştirme + ortak kiracı sınırlayıcı altyapısı

**Files:**
- Modify: `backend/pom.xml` (Caffeine bağımlılığı)
- Create: `backend/src/main/java/com/vetos/platform/ratelimit/package-info.java`
- Create: `backend/src/main/java/com/vetos/platform/ratelimit/RateLimitProperties.java`
- Create: `backend/src/main/java/com/vetos/platform/ratelimit/RateLimitConfig.java`
- Create: `backend/src/main/java/com/vetos/platform/ratelimit/TenantPool.java`
- Create: `backend/src/main/java/com/vetos/platform/ratelimit/ConsumeResult.java`
- Create: `backend/src/main/java/com/vetos/platform/ratelimit/TenantRateLimiter.java`
- Create: `backend/src/main/java/com/vetos/platform/ratelimit/RateLimitExceededException.java`
- Create: `backend/src/main/java/com/vetos/platform/ratelimit/RateLimitResponses.java`
- Modify: `backend/src/main/java/com/vetos/platform/web/RateLimitFilter.java`
- Modify: `backend/src/main/java/com/vetos/platform/web/GlobalExceptionHandler.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/vetos/platform/web/RateLimitFilterTest.java` (genişlet)
- Test: `backend/src/test/java/com/vetos/platform/ratelimit/TenantRateLimiterTest.java`
- Test: `backend/src/test/java/com/vetos/platform/web/GlobalExceptionHandlerRateLimitTest.java`

**Interfaces:**
- Produces: `TenantPool { GENERAL, AI, CAMPAIGN_RECIPIENTS }`; `record ConsumeResult(boolean consumed, long retryAfterSeconds)`; `TenantRateLimiter#tryConsume(TenantPool, UUID, long): ConsumeResult`, `#consumeOrThrow(TenantPool, UUID, long)` (throws `RateLimitExceededException`), `#refund(TenantPool, UUID, long)`; `RateLimitExceededException#getRetryAfterSeconds(): long`; `RateLimitResponses.write(HttpServletResponse, long retryAfterSeconds, String path)`, `RateLimitResponses.message(long)`; `RateLimitProperties(int tenantGeneralPerMinute, int tenantAiPerMinute, int campaignRecipientsPerHour)`; `TenantRateLimiter` Spring bean.

- [ ] **Step 1: Caffeine bağımlılığını ekle**

`backend/pom.xml` içinde `bucket4j-core` bağımlılığının hemen altına (sürüm Spring Boot BOM'dan gelir, yazma):

```xml
        <dependency>
            <groupId>com.github.ben-manes.caffeine</groupId>
            <artifactId>caffeine</artifactId>
        </dependency>
```

- [ ] **Step 2: Başarısız testleri yaz**

`backend/src/test/java/com/vetos/platform/ratelimit/TenantRateLimiterTest.java`:

```java
package com.vetos.platform.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantRateLimiterTest {

    private final TenantRateLimiter limiter = new TenantRateLimiter(new RateLimitProperties(3, 2, 100));

    @Test
    void should_allowUpToCapacity_then_rejectWithRetryAfter() {
        UUID tenant = UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            assertThat(limiter.tryConsume(TenantPool.GENERAL, tenant, 1).consumed()).isTrue();
        }
        ConsumeResult rejected = limiter.tryConsume(TenantPool.GENERAL, tenant, 1);
        assertThat(rejected.consumed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isBetween(1L, 60L);
    }

    @Test
    void should_keepTenantsAndPoolsSeparate() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        limiter.tryConsume(TenantPool.AI, a, 2);
        assertThat(limiter.tryConsume(TenantPool.AI, a, 1).consumed()).isFalse();
        assertThat(limiter.tryConsume(TenantPool.AI, b, 1).consumed()).isTrue();
        assertThat(limiter.tryConsume(TenantPool.GENERAL, a, 1).consumed()).isTrue();
    }

    @Test
    void should_rejectWithFiniteRetry_when_tokensExceedCapacity() {
        ConsumeResult result = limiter.tryConsume(TenantPool.CAMPAIGN_RECIPIENTS, UUID.randomUUID(), 101);
        assertThat(result.consumed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(3600L);
    }

    @Test
    void should_consumeNothing_when_tokensZero() {
        UUID tenant = UUID.randomUUID();
        assertThat(limiter.tryConsume(TenantPool.AI, tenant, 0).consumed()).isTrue();
        assertThat(limiter.tryConsume(TenantPool.AI, tenant, 2).consumed()).isTrue();
    }

    @Test
    void should_refundTokens() {
        UUID tenant = UUID.randomUUID();
        limiter.tryConsume(TenantPool.CAMPAIGN_RECIPIENTS, tenant, 100);
        limiter.refund(TenantPool.CAMPAIGN_RECIPIENTS, tenant, 40);
        assertThat(limiter.tryConsume(TenantPool.CAMPAIGN_RECIPIENTS, tenant, 40).consumed()).isTrue();
        assertThat(limiter.tryConsume(TenantPool.CAMPAIGN_RECIPIENTS, tenant, 1).consumed()).isFalse();
    }

    @Test
    void should_throwWithRetryAfter_on_consumeOrThrow() {
        UUID tenant = UUID.randomUUID();
        limiter.consumeOrThrow(TenantPool.AI, tenant, 2);
        assertThatThrownBy(() -> limiter.consumeOrThrow(TenantPool.AI, tenant, 1))
            .isInstanceOf(RateLimitExceededException.class)
            .satisfies(ex -> {
                RateLimitExceededException e = (RateLimitExceededException) ex;
                assertThat(e.getErrorCode()).isEqualTo("RATE_LIMITED");
                assertThat(e.getRetryAfterSeconds()).isPositive();
                assertThat(e.getMessage()).startsWith("Çok fazla istek gönderildi, lütfen ").endsWith(" saniye sonra tekrar deneyin.");
            });
    }

    @Test
    void should_roundRetrySecondsUp_minimumOne() {
        assertThat(TenantRateLimiter.toRetrySeconds(0)).isEqualTo(1);
        assertThat(TenantRateLimiter.toRetrySeconds(1)).isEqualTo(1);
        assertThat(TenantRateLimiter.toRetrySeconds(1_000_000_001L)).isEqualTo(2);
    }
}
```

`RateLimitFilterTest`'e eklenecek testler (mevcut testlere dokunma; mevcut `should_reject_when_authLimitExceeded` testi `setContentType("application/json")` doğrulamasıyla geçmeye devam etmeli):

```java
    @Test
    void should_setRetryAfter_and_writeErrorResponseBody_when_limited() throws ServletException, IOException {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        when(request.getRequestURI()).thenReturn("/api/v1/auth/login");
        when(request.getContextPath()).thenReturn("");
        when(request.getRemoteAddr()).thenReturn("10.0.0.50");
        StringWriter body = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(body));
        org.slf4j.MDC.put("requestId", "req-123");
        try {
            for (int i = 0; i < 11; i++) {
                filter.doFilter(request, response, chain);
            }
        } finally {
            org.slf4j.MDC.remove("requestId");
        }

        verify(response).setStatus(429);
        verify(response).setHeader(eq("Retry-After"), argThat(v -> Long.parseLong(v) >= 1));
        assertThat(body.toString())
            .contains("\"errorCode\":\"RATE_LIMITED\"")
            .contains("\"requestId\":\"req-123\"")
            .contains("\"path\":\"/api/v1/auth/login\"")
            .contains("saniye sonra tekrar deneyin");
    }

    @Test
    void should_boundTrackedClients_byMaximumSize() throws ServletException, IOException {
        RateLimitFilter small = new RateLimitFilter(5);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);
        for (int i = 0; i < 50; i++) {
            HttpServletRequest request = mock(HttpServletRequest.class);
            when(request.getRequestURI()).thenReturn("/api/v1/public/signup/plans");
            when(request.getContextPath()).thenReturn("");
            when(request.getRemoteAddr()).thenReturn("10.1.0." + i);
            small.doFilter(request, response, chain);
        }
        assertThat(small.trackedClientCount()).isLessThanOrEqualTo(5);
    }
```

(`eq` ve `argThat` `org.mockito.ArgumentMatchers`'tan; dosya zaten `org.mockito.Mockito.*` static import ediyor — `ArgumentMatchers.*` de ekle.)

`backend/src/test/java/com/vetos/platform/web/GlobalExceptionHandlerRateLimitTest.java`:

```java
package com.vetos.platform.web;

import com.vetos.platform.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerRateLimitTest {

    @Test
    void should_return429WithRetryAfter_for_rateLimitExceeded() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/api/v1/notifications/campaigns/send");

        var response = handler.handleRateLimited(new RateLimitExceededException(42), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("42");
        assertThat(response.getBody().errorCode()).isEqualTo("RATE_LIMITED");
        assertThat(response.getBody().message()).contains("42 saniye");
    }
}
```

- [ ] **Step 3: Testlerin derlenmediğini/başarısız olduğunu gör**

Run: `./mvnw -q test -Dtest='TenantRateLimiterTest,RateLimitFilterTest,GlobalExceptionHandlerRateLimitTest'`
Expected: derleme hatası (`TenantRateLimiter`, `RateLimitFilter(int)`, `handleRateLimited` yok).

- [ ] **Step 4: `platform.ratelimit` paketini yaz**

`package-info.java`:

```java
@org.springframework.modulith.NamedInterface("ratelimit")
package com.vetos.platform.ratelimit;
```

`RateLimitProperties.java`:

```java
package com.vetos.platform.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Kiraci basina koruma sinirlari (spec 2026-10-15 §1.1). Herkese ayni; paket kotalari ayri (platform.quota). */
@ConfigurationProperties("vetly.rate-limit")
public record RateLimitProperties(
    @DefaultValue("600") int tenantGeneralPerMinute,
    @DefaultValue("10") int tenantAiPerMinute,
    @DefaultValue("2000") int campaignRecipientsPerHour
) {}
```

`RateLimitConfig.java`:

```java
package com.vetos.platform.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
class RateLimitConfig {

    @Bean
    TenantRateLimiter tenantRateLimiter(RateLimitProperties properties) {
        return new TenantRateLimiter(properties);
    }
}
```

`TenantPool.java`:

```java
package com.vetos.platform.ratelimit;

public enum TenantPool { GENERAL, AI, CAMPAIGN_RECIPIENTS }
```

`ConsumeResult.java`:

```java
package com.vetos.platform.ratelimit;

public record ConsumeResult(boolean consumed, long retryAfterSeconds) {

    static ConsumeResult ok() {
        return new ConsumeResult(true, 0);
    }
}
```

`TenantRateLimiter.java`:

```java
package com.vetos.platform.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Kiraci basina bellek-ici kovalar (tek instance). Kova anahtari havuz + tenantId;
 * 2 saat kullanilmayan kova silinir, en fazla 50.000 kova tutulur -- bellek sinirli.
 */
public class TenantRateLimiter {

    private final Map<TenantPool, Bandwidth> limits = new EnumMap<>(TenantPool.class);
    private final Map<TenantPool, Duration> periods = new EnumMap<>(TenantPool.class);
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .expireAfterAccess(Duration.ofHours(2))
        .maximumSize(50_000)
        .build();

    public TenantRateLimiter(RateLimitProperties properties) {
        define(TenantPool.GENERAL, properties.tenantGeneralPerMinute(), Duration.ofMinutes(1));
        define(TenantPool.AI, properties.tenantAiPerMinute(), Duration.ofMinutes(1));
        define(TenantPool.CAMPAIGN_RECIPIENTS, properties.campaignRecipientsPerHour(), Duration.ofHours(1));
    }

    private void define(TenantPool pool, int capacity, Duration period) {
        limits.put(pool, Bandwidth.builder().capacity(capacity).refillGreedy(capacity, period).build());
        periods.put(pool, period);
    }

    public ConsumeResult tryConsume(TenantPool pool, UUID tenantId, long tokens) {
        if (tokens <= 0) {
            return ConsumeResult.ok();
        }
        if (tokens > limits.get(pool).getCapacity()) {
            // Kova kapasitesini asan istek hic karsilanamaz; Bucket4j'nin bekleme suresi anlamsiz
            // olur -- havuzun tam periyodu kadar beklemesini soyleriz.
            return new ConsumeResult(false, periods.get(pool).toSeconds());
        }
        ConsumptionProbe probe = bucket(pool, tenantId).tryConsumeAndReturnRemaining(tokens);
        if (probe.isConsumed()) {
            return ConsumeResult.ok();
        }
        return new ConsumeResult(false, toRetrySeconds(probe.getNanosToWaitForRefill()));
    }

    public void consumeOrThrow(TenantPool pool, UUID tenantId, long tokens) {
        ConsumeResult result = tryConsume(pool, tenantId, tokens);
        if (!result.consumed()) {
            throw new RateLimitExceededException(result.retryAfterSeconds());
        }
    }

    public void refund(TenantPool pool, UUID tenantId, long tokens) {
        if (tokens <= 0) {
            return;
        }
        Bucket bucket = buckets.getIfPresent(key(pool, tenantId));
        if (bucket != null) {
            bucket.addTokens(tokens);
        }
    }

    public static long toRetrySeconds(long nanos) {
        return Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
    }

    private Bucket bucket(TenantPool pool, UUID tenantId) {
        return buckets.get(key(pool, tenantId), k -> Bucket.builder().addLimit(limits.get(pool)).build());
    }

    private static String key(TenantPool pool, UUID tenantId) {
        return pool.name() + ":" + tenantId;
    }
}
```

`RateLimitExceededException.java`:

```java
package com.vetos.platform.ratelimit;

import com.vetos.platform.exception.DomainException;

/** GlobalExceptionHandler'da ozel handler: 429 + Retry-After. */
public class RateLimitExceededException extends DomainException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(long retryAfterSeconds) {
        super("RATE_LIMITED", RateLimitResponses.message(retryAfterSeconds));
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
```

`RateLimitResponses.java`:

```java
package com.vetos.platform.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vetos.platform.web.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Filtrelerden (Spring MVC disinda) yazilan 429 yaniti -- govde, GlobalExceptionHandler'in
 * urettigi ErrorResponse ile ayni bicimde (requestId dahil; RequestIdFilter once calisir).
 */
public final class RateLimitResponses {

    private static final ObjectMapper MAPPER = JsonMapper.builder()
        .addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build();

    private RateLimitResponses() {
    }

    public static String message(long retryAfterSeconds) {
        return "Çok fazla istek gönderildi, lütfen " + retryAfterSeconds + " saniye sonra tekrar deneyin.";
    }

    public static void write(HttpServletResponse response, long retryAfterSeconds, String path) throws IOException {
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(
            ErrorResponse.of("RATE_LIMITED", message(retryAfterSeconds), path)
        ));
    }
}
```

- [ ] **Step 5: `RateLimitFilter`'ı Caffeine'e ve yeni 429 yanıtına taşı**

Dört `Map<String, Bucket> ... = new ConcurrentHashMap<>()` alanını Caffeine önbelleğine çevir; kova seçimi ve sayılar (`Bandwidth` tanımları) aynen kalsın. Değişen parçalar:

```java
    private static final long DEFAULT_MAX_TRACKED_CLIENTS = 100_000;

    private final Cache<String, Bucket> authBuckets;
    private final Cache<String, Bucket> publicBuckets;
    private final Cache<String, Bucket> platformAdminAuthBuckets;
    private final Cache<String, Bucket> extensionPairBuckets;

    public RateLimitFilter() {
        this(DEFAULT_MAX_TRACKED_CLIENTS);
    }

    /** Test icin kucuk ust sinir. Tahliye senkron (executor = calling thread) -- olcum deterministik. */
    RateLimitFilter(long maxTrackedClientsPerPool) {
        this.authBuckets = newCache(maxTrackedClientsPerPool);
        this.publicBuckets = newCache(maxTrackedClientsPerPool);
        this.platformAdminAuthBuckets = newCache(maxTrackedClientsPerPool);
        this.extensionPairBuckets = newCache(maxTrackedClientsPerPool);
    }

    private static Cache<String, Bucket> newCache(long maximumSize) {
        // 10 dk kullanilmayan IP kaydi silinir; sahte IP selinde bellek sinirsiz buyumez.
        return Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(10))
            .maximumSize(maximumSize)
            .executor(Runnable::run)
            .build();
    }

    long trackedClientCount() {
        long total = 0;
        for (Cache<String, Bucket> cache : List.of(authBuckets, publicBuckets, platformAdminAuthBuckets, extensionPairBuckets)) {
            cache.cleanUp();
            total += cache.estimatedSize();
        }
        return total;
    }
```

`doFilterInternal` içinde `Map<String, Bucket> buckets;` → `Cache<String, Bucket> buckets;` ve kova alma + red kısmı:

```java
        String clientIp = request.getRemoteAddr();
        Bucket bucket = buckets.get(clientIp, ip -> Bucket.builder().addLimit(limit).build());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            RateLimitResponses.write(response, TenantRateLimiter.toRetrySeconds(probe.getNanosToWaitForRefill()), path);
        }
```

(`TenantRateLimiter.toRetrySeconds` public static — `RateLimitFilter` farklı pakette.)

Eskimiş X-Forwarded-For yorumunu şu yorumla değiştir:

```java
        // getRemoteAddr(): prod'da SERVER_FORWARD_HEADERS_STRATEGY=native ile Tomcat RemoteIpValve,
        // guvenilir proxy'den (127.0.0.1, nginx) gelen X-Forwarded-For'daki istemci IP'sini buraya
        // yansitir; nginx header'i $remote_addr ile UZERINE yazar (bkz. docs/deployment.md). Header'i
        // burada elle okumak sahteciligi geri getirir -- yapma.
```

Sınıf Javadoc'undaki "kiraci basina adil paylasim ... bu turun kapsaminda degil" cümlesini "Kimlik dogrulanmis trafik icin bkz. TenantRateLimitFilter." ile değiştir.

- [ ] **Step 6: `GlobalExceptionHandler`'a 429 handler'ı ekle**

`handleDomainException`'ın hemen altına (Spring en özel tipi seçer):

```java
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponse> handleRateLimited(RateLimitExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
            .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
    }
```

- [ ] **Step 7: `application.yml`'a ayarları ekle**

Dosyanın sonuna (mevcut `features:` bloğundan sonra):

```yaml
vetly:
  # Kiraci basina koruma sinirlari (bellek-ici, herkese ayni) -- spec 2026-10-15 §1.
  # Env ile degistirilebilir: VETLY_RATE_LIMIT_TENANT_GENERAL_PER_MINUTE vb.
  rate-limit:
    tenant-general-per-minute: 600
    tenant-ai-per-minute: 10
    campaign-recipients-per-hour: 2000
```

(Dosyada zaten bir `vetly:` kökü varsa altına ekle — iki `vetly:` kökü YAML'da hatadır; önce `grep -n "^vetly:" application.yml` ile kontrol et.)

- [ ] **Step 8: Testleri çalıştır**

Run: `./mvnw -q test -Dtest='TenantRateLimiterTest,RateLimitFilterTest,RateLimitFilterExtensionPairTest,GlobalExceptionHandlerRateLimitTest'`
Expected: PASS (mevcut RateLimitFilter testleri dahil).

---

### Task 2: `TenantRateLimitFilter` (genel tavan + AI dakikalık sınır) ve zincir bağlantısı

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/web/TenantRateLimitFilter.java`
- Modify: `backend/src/main/java/com/vetos/platform/security/SecurityConfig.java` (bean metodu imzası + filtre kaydı)
- Modify: `backend/src/main/java/com/vetos/platform/security/TarbilExtensionSecurityConfig.java` (aynı)
- Test: `backend/src/test/java/com/vetos/platform/web/TenantRateLimitFilterTest.java`
- Test: `backend/src/test/java/com/vetos/platform/web/TenantRateLimitWiringTest.java`

**Interfaces:**
- Consumes: `TenantRateLimiter#tryConsume`, `TenantPool`, `ConsumeResult`, `RateLimitResponses.write` (Task 1); `TenantContext.currentOrNull()`.
- Produces: `TenantRateLimitFilter(TenantRateLimiter)`, `TenantRateLimitFilter.AI_GENERATION_PATHS: Set<String>`.

- [ ] **Step 1: Başarısız birim testini yaz**

`TenantRateLimitFilterTest.java`:

```java
package com.vetos.platform.web;

import com.vetos.platform.ratelimit.RateLimitProperties;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class TenantRateLimitFilterTest {

    private final TenantRateLimiter limiter = new TenantRateLimiter(new RateLimitProperties(3, 1, 2000));
    private final TenantRateLimitFilter filter = new TenantRateLimitFilter(limiter);

    @AfterEach
    void clear() {
        TenantContext.clear();
    }

    private MockHttpServletResponse call(String method, String uri) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    @Test
    void should_passThrough_when_noTenant() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(call("GET", "/api/v1/platform-admin/plans").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void should_rejectWith429_when_generalLimitExceeded() throws Exception {
        TenantContext.set(UUID.randomUUID());
        for (int i = 0; i < 3; i++) {
            assertThat(call("GET", "/api/v1/patients").getStatus()).isEqualTo(200);
        }
        MockHttpServletResponse rejected = call("GET", "/api/v1/patients");
        assertThat(rejected.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(rejected.getHeader("Retry-After"))).isPositive();
        assertThat(rejected.getContentAsString()).contains("\"errorCode\":\"RATE_LIMITED\"");
    }

    @Test
    void should_applyAiLimit_onlyToGenerationPosts() throws Exception {
        TenantContext.set(UUID.randomUUID());
        assertThat(call("POST", "/api/v1/ai/diagnosis-suggestions").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/ai/diagnosis-suggestions/" + UUID.randomUUID() + "/decision").getStatus()).isEqualTo(200);
        assertThat(call("POST", "/api/v1/ai/soap-drafts").getStatus()).isEqualTo(429);
    }

    @Test
    void should_notConsumeAiToken_when_generalLimitRejects() throws Exception {
        UUID tenant = UUID.randomUUID();
        TenantContext.set(tenant);
        for (int i = 0; i < 3; i++) {
            call("GET", "/api/v1/patients");
        }
        assertThat(call("POST", "/api/v1/ai/soap-drafts").getStatus()).isEqualTo(429);
        assertThat(limiter.tryConsume(com.vetos.platform.ratelimit.TenantPool.AI, tenant, 1).consumed()).isTrue();
    }

    @Test
    void should_coverAllThreeGenerationEndpoints() {
        assertThat(TenantRateLimitFilter.AI_GENERATION_PATHS).containsExactlyInAnyOrder(
            "/api/v1/ai/soap-drafts", "/api/v1/ai/treatment-recommendations", "/api/v1/ai/diagnosis-suggestions"
        );
    }
}
```

`TenantRateLimitWiringTest.java` (canlı Postgres):

```java
package com.vetos.platform.web;

import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. Filtrenin JWT'den SONRA calistigini gercek zincirde dogrular. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "vetly.rate-limit.tenant-general-per-minute=5",
    "vetly.rate-limit.tenant-ai-per-minute=2"
})
class TenantRateLimitWiringTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private String bearer(UUID tenantId) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), "VET");
    }

    @Test
    void should_return429_after_tenantGeneralLimit() throws Exception {
        String token = bearer(UUID.randomUUID());
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(get("/api/v1/subscriptions/features").header("Authorization", token))
                .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/subscriptions/features").header("Authorization", token))
            .andExpect(status().isTooManyRequests())
            .andExpect(header().exists("Retry-After"))
            .andExpect(jsonPath("$.errorCode").value("RATE_LIMITED"))
            .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void should_limitAiGeneration_perTenant() throws Exception {
        String token = bearer(UUID.randomUUID());
        // Bos govde -> controller'da 400 (AI cagrilmaz); filtre jetonu ondan once harcar.
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/ai/diagnosis-suggestions").header("Authorization", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        }
        mockMvc.perform(post("/api/v1/ai/diagnosis-suggestions").header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isTooManyRequests());

        String otherTenant = bearer(UUID.randomUUID());
        mockMvc.perform(post("/api/v1/ai/diagnosis-suggestions").header("Authorization", otherTenant)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void should_notLimitAnonymousPublicTraffic_byTenantFilter() throws Exception {
        for (int i = 0; i < 8; i++) {
            mockMvc.perform(get("/api/v1/public/signup/plans")).andExpect(status().is2xxSuccessful());
        }
    }
}
```

(`/api/v1/public/signup/plans` var olan anonim GET uçtur; amaç 5'ten fazla anonim isteğin kiracı filtresine takılmadığını göstermek — public IP limiti 60/dk.)

- [ ] **Step 2: Testlerin başarısız olduğunu gör**

Run: `./mvnw -q test -Dtest='TenantRateLimitFilterTest'`
Expected: derleme hatası (`TenantRateLimitFilter` yok).

- [ ] **Step 3: Filtreyi yaz**

```java
package com.vetos.platform.web;

import com.vetos.platform.ratelimit.ConsumeResult;
import com.vetos.platform.ratelimit.RateLimitResponses;
import com.vetos.platform.ratelimit.TenantPool;
import com.vetos.platform.ratelimit.TenantRateLimiter;
import com.vetos.platform.tenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

/**
 * Kimlik dogrulanmis trafik icin kiraci basina koruma sinirlari (spec 2026-10-15 §1.3).
 * Kimlik dogrulama filtresinden SONRA calisir (TenantContext dolu); kiracisiz istekler
 * (anonim, platform admin, gecersiz token) dokunulmadan gecer.
 */
public class TenantRateLimitFilter extends OncePerRequestFilter {

    static final Set<String> AI_GENERATION_PATHS = Set.of(
        "/api/v1/ai/soap-drafts",
        "/api/v1/ai/treatment-recommendations",
        "/api/v1/ai/diagnosis-suggestions"
    );

    private static final UrlPathHelper PATH_HELPER = new UrlPathHelper();

    private final TenantRateLimiter limiter;

    public TenantRateLimitFilter(TenantRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request, HttpServletResponse response, FilterChain filterChain
    ) throws ServletException, IOException {
        UUID tenantId = TenantContext.currentOrNull();
        if (tenantId == null) {
            filterChain.doFilter(request, response);
            return;
        }
        // Decode edilmis, uygulama-goreli path (RateLimitFilter ile ayni gerekce: percent-encoding bypass'i).
        String path = PATH_HELPER.getPathWithinApplication(request);

        ConsumeResult general = limiter.tryConsume(TenantPool.GENERAL, tenantId, 1);
        if (!general.consumed()) {
            RateLimitResponses.write(response, general.retryAfterSeconds(), path);
            return;
        }
        if ("POST".equals(request.getMethod()) && AI_GENERATION_PATHS.contains(path)) {
            ConsumeResult ai = limiter.tryConsume(TenantPool.AI, tenantId, 1);
            if (!ai.consumed()) {
                RateLimitResponses.write(response, ai.retryAfterSeconds(), path);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 4: Zincirlere bağla**

`SecurityConfig.securityFilterChain(HttpSecurity http, Environment env)` imzasına `TenantRateLimiter tenantRateLimiter` parametresini ekle (Spring bean metodu parametresini enjekte eder). Mevcut üç `addFilter...` satırından sonra:

```java
            .addFilterAfter(new TenantRateLimitFilter(tenantRateLimiter), JwtAuthenticationFilter.class);
```

(Yani mevcut son satırdaki `;` kaldırılıp zincire eklenir. `JwtAuthenticationFilter` bu noktada zaten kayıtlı olduğu için "registered order" hatası olmaz.)

`TarbilExtensionSecurityConfig.tarbilExtensionSecurityFilterChain(HttpSecurity http)` imzasına aynı parametreyi ekle ve:

```java
            .addFilterAfter(new TenantRateLimitFilter(tenantRateLimiter), ExtensionTokenAuthenticationFilter.class);
```

`PlatformAdminSecurityConfig`'e DOKUNMA.

- [ ] **Step 5: Testleri çalıştır**

Run: `./mvnw -q test -Dtest='TenantRateLimitFilterTest,TenantRateLimitWiringTest,TarbilExtensionSecurityIntegrationTest,RateLimitFilterTest'`
Expected: PASS.

---

### Task 3: Kota çekirdeği — migration, `platform.quota` portu, atomik sayaç, paket kotası çözümü

**Files:**
- Create: `backend/src/main/resources/db/migration/V80__plan_quotas_monthly_usage.sql`
- Create: `backend/src/main/java/com/vetos/platform/quota/package-info.java`
- Create: `backend/src/main/java/com/vetos/platform/quota/UsageKind.java`
- Create: `backend/src/main/java/com/vetos/platform/quota/UsageQuotaPort.java`
- Create: `backend/src/main/java/com/vetos/platform/quota/UsageSnapshot.java`
- Create: `backend/src/main/java/com/vetos/platform/quota/MonthlyQuotaExceededException.java`
- Create: `backend/src/main/java/com/vetos/platform/quota/QuotaProperties.java`
- Create: `backend/src/main/java/com/vetos/platform/quota/QuotaConfig.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/persistence/MonthlyUsageStore.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanUsageQuotaAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/domain/Plan.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/package-info.java` (`"platform::quota"` ekle)
- Modify: `backend/src/main/java/com/vetos/platform/web/GlobalExceptionHandler.java`
- Modify: `backend/src/main/resources/application.yml`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/persistence/MonthlyUsageStoreTest.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanUsageQuotaAdapterTest.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/domain/PlanTest.java` (genişlet)
- Test: `backend/src/test/java/com/vetos/platform/quota/MonthlyQuotaExceededExceptionTest.java`

**Interfaces:**
- Produces:
  - `enum UsageKind { AI, MESSAGE }`
  - `interface UsageQuotaPort { void reserve(UUID tenantId, UsageKind kind, int amount); void recordUnconditional(UUID tenantId, UsageKind kind, int amount); void release(UUID tenantId, UsageKind kind, int amount); UsageSnapshot currentUsage(UUID tenantId); }`
  - `record UsageSnapshot(String period, boolean enforced, Item ai, Item message)`, `record UsageSnapshot.Item(int used, Integer quota)` (quota null = sınırsız)
  - `MonthlyQuotaExceededException(UsageKind kind, int used, int quota, int requested)` — `errorCode` `MONTHLY_QUOTA_EXCEEDED`, HTTP 429
  - `record QuotaProperties(Trial trial)`, `record QuotaProperties.Trial(int ai, int message)` — Spring bean
  - `Plan#getMonthlyAiQuota(): Integer`, `Plan#getMonthlyMessageQuota(): Integer`, `Plan#updateQuotas(Integer, Integer)`

- [ ] **Step 1: Migration**

`V80__plan_quotas_monthly_usage.sql`:

```sql
-- Faz 4 (spec 2026-10-15): paket kotalari. NULL = sinirsiz -> mevcut paketlerde davranis degismez.
ALTER TABLE plans ADD COLUMN monthly_ai_quota INTEGER CHECK (monthly_ai_quota >= 0);
ALTER TABLE plans ADD COLUMN monthly_message_quota INTEGER CHECK (monthly_message_quota >= 0);

-- Kiraci + ay (Europe/Istanbul, 'YYYY-MM') + tur basina sayac. Platform duzeyi tablo;
-- erisim yalnizca UsageQuotaPort uzerinden (MonthlyUsageStore, native SQL).
CREATE TABLE tenant_monthly_usage (
    tenant_id UUID        NOT NULL,
    period    CHAR(7)     NOT NULL,
    kind      VARCHAR(16) NOT NULL,
    used      INTEGER     NOT NULL DEFAULT 0 CHECK (used >= 0),
    PRIMARY KEY (tenant_id, period, kind)
);
```

- [ ] **Step 2: Başarısız testleri yaz**

`MonthlyUsageStoreTest.java` (canlı Postgres):

```java
package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.platform.quota.UsageKind;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/** Canli docker-compose Postgres gerekir. Kota dogrulugu SQL'e bagli oldugu icin gercek DB'de. */
@SpringBootTest
class MonthlyUsageStoreTest {

    @Autowired private MonthlyUsageStore store;

    @Test
    void should_incrementUpToQuota_then_rejectWithoutWriting() {
        UUID tenant = UUID.randomUUID();
        assertThat(store.tryIncrement(tenant, "2026-10", UsageKind.AI, 2, 3)).isTrue();
        assertThat(store.tryIncrement(tenant, "2026-10", UsageKind.AI, 2, 3)).isFalse();
        assertThat(store.used(tenant, "2026-10", UsageKind.AI)).isEqualTo(2);
        assertThat(store.tryIncrement(tenant, "2026-10", UsageKind.AI, 1, 3)).isTrue();
        assertThat(store.used(tenant, "2026-10", UsageKind.AI)).isEqualTo(3);
    }

    @Test
    void should_rejectEverything_when_quotaZero() {
        UUID tenant = UUID.randomUUID();
        assertThat(store.tryIncrement(tenant, "2026-10", UsageKind.MESSAGE, 1, 0)).isFalse();
        assertThat(store.used(tenant, "2026-10", UsageKind.MESSAGE)).isZero();
    }

    @Test
    void should_allowExactlyRemaining_underConcurrency() throws Exception {
        UUID tenant = UUID.randomUUID();
        int quota = 7;
        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                Callable<Boolean> task = () -> {
                    start.await();
                    return store.tryIncrement(tenant, "2026-10", UsageKind.AI, 1, quota);
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            long successes = 0;
            for (Future<Boolean> f : results) {
                if (f.get()) successes++;
            }
            assertThat(successes).isEqualTo(quota);
            assertThat(store.used(tenant, "2026-10", UsageKind.AI)).isEqualTo(quota);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void should_incrementUnconditionally_and_decrementWithFloorZero() {
        UUID tenant = UUID.randomUUID();
        store.increment(tenant, "2026-10", UsageKind.MESSAGE, 5);
        store.increment(tenant, "2026-10", UsageKind.MESSAGE, 4);
        assertThat(store.used(tenant, "2026-10", UsageKind.MESSAGE)).isEqualTo(9);
        store.decrement(tenant, "2026-10", UsageKind.MESSAGE, 20);
        assertThat(store.used(tenant, "2026-10", UsageKind.MESSAGE)).isZero();
    }

    @Test
    void should_keepPeriodsAndKindsSeparate() {
        UUID tenant = UUID.randomUUID();
        store.increment(tenant, "2026-10", UsageKind.AI, 3);
        assertThat(store.used(tenant, "2026-11", UsageKind.AI)).isZero();
        assertThat(store.used(tenant, "2026-10", UsageKind.MESSAGE)).isZero();
    }
}
```

`PlanUsageQuotaAdapterTest.java` (Mockito):

```java
package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.infrastructure.persistence.MonthlyUsageStore;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.quota.MonthlyQuotaExceededException;
import com.vetos.platform.quota.QuotaProperties;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanUsageQuotaAdapterTest {

    private static final UUID TENANT = UUID.randomUUID();
    // 2026-10-15T09:00Z -> Istanbul 12:00, donem 2026-10
    private static final Clock OCT = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneOffset.UTC);

    private final TenantAdminPort tenantAdminPort = mock(TenantAdminPort.class);
    private final PlanRepository planRepository = mock(PlanRepository.class);
    private final MonthlyUsageStore store = mock(MonthlyUsageStore.class);
    private final QuotaProperties props = new QuotaProperties(new QuotaProperties.Trial(30, 100));

    private PlanUsageQuotaAdapter adapter(boolean enforcement, Clock clock) {
        return new PlanUsageQuotaAdapter(tenantAdminPort, planRepository, store, props, enforcement, clock);
    }

    private Plan planWithQuotas(Integer ai, Integer message) {
        Plan plan = Plan.create("PRO", "Pro", BigDecimal.TEN);
        plan.updateQuotas(ai, message);
        return plan;
    }

    @Test
    void should_countButNeverBlock_when_enforcementDisabled() {
        adapter(false, OCT).reserve(TENANT, UsageKind.AI, 1);
        verify(store).increment(TENANT, "2026-10", UsageKind.AI, 1);
        verify(store, never()).tryIncrement(any(), any(), any(), anyInt(), anyInt());
        verifyNoInteractions(tenantAdminPort);
    }

    @Test
    void should_treatNullPlanQuotaAsUnlimited() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("PRO"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(planWithQuotas(null, 500)));

        adapter(true, OCT).reserve(TENANT, UsageKind.AI, 1);

        verify(store).increment(TENANT, "2026-10", UsageKind.AI, 1);
        verify(store, never()).tryIncrement(any(), any(), any(), anyInt(), anyInt());
    }

    @Test
    void should_reserveAgainstPlanQuota() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("PRO"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(planWithQuotas(100, 500)));
        when(store.tryIncrement(TENANT, "2026-10", UsageKind.MESSAGE, 300, 500)).thenReturn(true);

        adapter(true, OCT).reserve(TENANT, UsageKind.MESSAGE, 300);

        verify(store).tryIncrement(TENANT, "2026-10", UsageKind.MESSAGE, 300, 500);
    }

    @Test
    void should_throwWithUsage_when_quotaInsufficient() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("PRO"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(planWithQuotas(100, 500)));
        when(store.tryIncrement(TENANT, "2026-10", UsageKind.MESSAGE, 300, 500)).thenReturn(false);
        when(store.used(TENANT, "2026-10", UsageKind.MESSAGE)).thenReturn(380);

        assertThatThrownBy(() -> adapter(true, OCT).reserve(TENANT, UsageKind.MESSAGE, 300))
            .isInstanceOf(MonthlyQuotaExceededException.class)
            .hasMessage("Bu ayki mesaj hakkınız yetersiz: 300 alıcı, kalan 120.");
    }

    @Test
    void should_useTrialQuotasFromProperties() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("TRIAL"));
        when(store.tryIncrement(TENANT, "2026-10", UsageKind.AI, 1, 30)).thenReturn(true);

        adapter(true, OCT).reserve(TENANT, UsageKind.AI, 1);

        verify(store).tryIncrement(TENANT, "2026-10", UsageKind.AI, 1, 30);
        verifyNoInteractions(planRepository);
    }

    @Test
    void should_useZeroQuota_when_noSubscription() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.empty());
        when(store.tryIncrement(TENANT, "2026-10", UsageKind.AI, 1, 0)).thenReturn(false);

        assertThatThrownBy(() -> adapter(true, OCT).reserve(TENANT, UsageKind.AI, 1))
            .isInstanceOf(MonthlyQuotaExceededException.class);
    }

    @Test
    void should_useZeroQuota_when_planRowMissing() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("GONE"));
        when(planRepository.findByCode("GONE")).thenReturn(Optional.empty());
        when(store.tryIncrement(TENANT, "2026-10", UsageKind.AI, 1, 0)).thenReturn(false);

        assertThatThrownBy(() -> adapter(true, OCT).reserve(TENANT, UsageKind.AI, 1))
            .isInstanceOf(MonthlyQuotaExceededException.class);
    }

    @Test
    void should_useIstanbulCalendarMonth() {
        Clock lateOctoberUtc = Clock.fixed(Instant.parse("2026-10-31T21:30:00Z"), ZoneOffset.UTC);
        adapter(false, lateOctoberUtc).recordUnconditional(TENANT, UsageKind.MESSAGE, 1);
        verify(store).increment(TENANT, "2026-11", UsageKind.MESSAGE, 1);
    }

    @Test
    void should_doNothing_forNonPositiveAmounts() {
        PlanUsageQuotaAdapter a = adapter(true, OCT);
        a.reserve(TENANT, UsageKind.MESSAGE, 0);
        a.recordUnconditional(TENANT, UsageKind.MESSAGE, 0);
        a.release(TENANT, UsageKind.MESSAGE, 0);
        verifyNoInteractions(store, tenantAdminPort);
    }

    @Test
    void should_releaseViaDecrement() {
        adapter(true, OCT).release(TENANT, UsageKind.AI, 1);
        verify(store).decrement(TENANT, "2026-10", UsageKind.AI, 1);
    }

    @Test
    void should_reportCurrentUsage_withResolvedQuotas() {
        when(tenantAdminPort.findPlanCode(TENANT)).thenReturn(Optional.of("PRO"));
        when(planRepository.findByCode("PRO")).thenReturn(Optional.of(planWithQuotas(100, null)));
        when(store.used(TENANT, "2026-10", UsageKind.AI)).thenReturn(34);
        when(store.used(TENANT, "2026-10", UsageKind.MESSAGE)).thenReturn(1240);

        UsageSnapshot snapshot = adapter(false, OCT).currentUsage(TENANT);

        assertThat(snapshot).isEqualTo(new UsageSnapshot(
            "2026-10", false, new UsageSnapshot.Item(34, 100), new UsageSnapshot.Item(1240, null)
        ));
    }
}
```

`PlanTest`'e ekle:

```java
    @Test
    void should_updateQuotas_allowingNullAsUnlimited() {
        Plan plan = Plan.create("PRO", "Pro", BigDecimal.TEN);
        plan.updateQuotas(100, null);
        assertThat(plan.getMonthlyAiQuota()).isEqualTo(100);
        assertThat(plan.getMonthlyMessageQuota()).isNull();
    }

    @Test
    void should_rejectNegativeQuota() {
        Plan plan = Plan.create("PRO", "Pro", BigDecimal.TEN);
        assertThatThrownBy(() -> plan.updateQuotas(-1, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> plan.updateQuotas(null, -5)).isInstanceOf(IllegalArgumentException.class);
    }
```

(`PlanTest`'in mevcut import'larına `assertThatThrownBy` ve `BigDecimal` yoksa ekle.)

`MonthlyQuotaExceededExceptionTest.java`:

```java
package com.vetos.platform.quota;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MonthlyQuotaExceededExceptionTest {

    @Test
    void should_buildAiMessage() {
        var ex = new MonthlyQuotaExceededException(UsageKind.AI, 100, 100, 1);
        assertThat(ex.getErrorCode()).isEqualTo("MONTHLY_QUOTA_EXCEEDED");
        assertThat(ex.getMessage()).isEqualTo("Bu ayki AI hakkınız doldu (100/100). Paketinizi yükseltmek için yöneticinize başvurun.");
    }

    @Test
    void should_buildMessageMessage_withRemainingNeverNegative() {
        assertThat(new MonthlyQuotaExceededException(UsageKind.MESSAGE, 1240, 1000, 5).getMessage())
            .isEqualTo("Bu ayki mesaj hakkınız yetersiz: 5 alıcı, kalan 0.");
    }
}
```

Ayrıca `GlobalExceptionHandlerRateLimitTest`'e ekle:

```java
    @Test
    void should_return429WithoutRetryAfter_for_monthlyQuota() {
        var handler = new GlobalExceptionHandler();
        var request = new MockHttpServletRequest("POST", "/api/v1/ai/soap-drafts");

        var response = handler.handleMonthlyQuota(
            new com.vetos.platform.quota.MonthlyQuotaExceededException(com.vetos.platform.quota.UsageKind.AI, 30, 30, 1), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().containsKey("Retry-After")).isFalse();
        assertThat(response.getBody().errorCode()).isEqualTo("MONTHLY_QUOTA_EXCEEDED");
    }
```

- [ ] **Step 3: Testlerin başarısız olduğunu gör**

Run: `./mvnw -q test -Dtest='PlanUsageQuotaAdapterTest,MonthlyQuotaExceededExceptionTest,PlanTest'`
Expected: derleme hatası.

- [ ] **Step 4: `platform.quota` paketini yaz**

`package-info.java`:

```java
@org.springframework.modulith.NamedInterface("quota")
package com.vetos.platform.quota;
```

`UsageKind.java`:

```java
package com.vetos.platform.quota;

/** MESSAGE = SMS + WhatsApp tek kota. */
public enum UsageKind { AI, MESSAGE }
```

`UsageQuotaPort.java`:

```java
package com.vetos.platform.quota;

import java.util.UUID;

/**
 * Paket kotalari (spec 2026-10-15 §2). Her islem kendi transaction'inda calisir (REQUIRES_NEW):
 * cagiran islem geri alinirsa sayim kendiliginden geri ALINMAZ -- gerekiyorsa release() cagir.
 */
public interface UsageQuotaPort {

    /** Kota yetiyorsa amount kadar ayirir; yetmiyorsa hicbir sey yazmaz ve MonthlyQuotaExceededException firlatir. */
    void reserve(UUID tenantId, UsageKind kind, int amount);

    /** Her zaman sayar, asla engellemez (otomatik mesajlar). */
    void recordUnconditional(UUID tenantId, UsageKind kind, int amount);

    /** Ayrilmis hakki iade eder (0'in altina inmez). */
    void release(UUID tenantId, UsageKind kind, int amount);

    /** Bu ayin kullanimi ve kotalari (null kota = sinirsiz). */
    UsageSnapshot currentUsage(UUID tenantId);
}
```

`UsageSnapshot.java`:

```java
package com.vetos.platform.quota;

/** enforced=false: kisitlama kapali, sayim var ama engel yok. Item.quota null = sinirsiz. */
public record UsageSnapshot(String period, boolean enforced, Item ai, Item message) {

    public record Item(int used, Integer quota) {}
}
```

`MonthlyQuotaExceededException.java`:

```java
package com.vetos.platform.quota;

import com.vetos.platform.exception.DomainException;

/** GlobalExceptionHandler'da ozel handler: 429, Retry-After YOK (ay basinda acilir). */
public class MonthlyQuotaExceededException extends DomainException {

    public MonthlyQuotaExceededException(UsageKind kind, int used, int quota, int requested) {
        super("MONTHLY_QUOTA_EXCEEDED", messageFor(kind, used, quota, requested));
    }

    private static String messageFor(UsageKind kind, int used, int quota, int requested) {
        if (kind == UsageKind.AI) {
            return "Bu ayki AI hakkınız doldu (" + used + "/" + quota + "). Paketinizi yükseltmek için yöneticinize başvurun.";
        }
        int remaining = Math.max(0, quota - used);
        return "Bu ayki mesaj hakkınız yetersiz: " + requested + " alıcı, kalan " + remaining + ".";
    }
}
```

`QuotaProperties.java`:

```java
package com.vetos.platform.quota;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** TRIAL'in plans tablosunda satiri yok (kod ismen taniyor) -- deneme kotalari burada. */
@ConfigurationProperties("vetly.quota")
public record QuotaProperties(@DefaultValue Trial trial) {

    public record Trial(@DefaultValue("30") int ai, @DefaultValue("100") int message) {}
}
```

`QuotaConfig.java`:

```java
package com.vetos.platform.quota;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QuotaProperties.class)
class QuotaConfig {
}
```

- [ ] **Step 5: `Plan` alanları**

`Plan.java`'ya `active` alanından önce:

```java
    /** Aylik AI kotasi; null = sinirsiz (bkz. UsageQuotaPort). */
    @Column(name = "monthly_ai_quota")
    private Integer monthlyAiQuota;

    /** Aylik mesaj (SMS + WhatsApp) kotasi; null = sinirsiz. */
    @Column(name = "monthly_message_quota")
    private Integer monthlyMessageQuota;
```

ve `deactivate()`'ten sonra:

```java
    public void updateQuotas(Integer monthlyAiQuota, Integer monthlyMessageQuota) {
        if ((monthlyAiQuota != null && monthlyAiQuota < 0) || (monthlyMessageQuota != null && monthlyMessageQuota < 0)) {
            throw new IllegalArgumentException("Kota negatif olamaz");
        }
        this.monthlyAiQuota = monthlyAiQuota;
        this.monthlyMessageQuota = monthlyMessageQuota;
    }
```

- [ ] **Step 6: `MonthlyUsageStore`**

```java
package com.vetos.modules.platformadmin.infrastructure.persistence;

import com.vetos.platform.quota.UsageKind;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * tenant_monthly_usage sayaci. Her yazma REQUIRES_NEW: uzun AI cagrisi boyunca satir kilidi
 * tutulmaz, ayrilan hak esZamanli isteklerce hemen gorulur. Kota kontrolu + artirma TEK
 * UPDATE'te (WHERE used + n <= quota): READ COMMITTED'da esZamanli UPDATE'ler satirda sirayla
 * bekler ve kosulu yeni degerle yeniden degerlendirir -- kota delinmez.
 */
@Repository
public class MonthlyUsageStore {

    private final NamedParameterJdbcTemplate jdbc;

    public MonthlyUsageStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean tryIncrement(UUID tenantId, String period, UsageKind kind, int amount, int quota) {
        MapSqlParameterSource p = key(tenantId, period, kind).addValue("n", amount).addValue("quota", quota);
        jdbc.update("""
            INSERT INTO tenant_monthly_usage (tenant_id, period, kind, used)
            VALUES (:tenantId, :period, :kind, 0)
            ON CONFLICT DO NOTHING
            """, p);
        return jdbc.update("""
            UPDATE tenant_monthly_usage SET used = used + :n
            WHERE tenant_id = :tenantId AND period = :period AND kind = :kind AND used + :n <= :quota
            """, p) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void increment(UUID tenantId, String period, UsageKind kind, int amount) {
        jdbc.update("""
            INSERT INTO tenant_monthly_usage (tenant_id, period, kind, used)
            VALUES (:tenantId, :period, :kind, :n)
            ON CONFLICT (tenant_id, period, kind) DO UPDATE SET used = tenant_monthly_usage.used + EXCLUDED.used
            """, key(tenantId, period, kind).addValue("n", amount));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void decrement(UUID tenantId, String period, UsageKind kind, int amount) {
        jdbc.update("""
            UPDATE tenant_monthly_usage SET used = GREATEST(used - :n, 0)
            WHERE tenant_id = :tenantId AND period = :period AND kind = :kind
            """, key(tenantId, period, kind).addValue("n", amount));
    }

    public int used(UUID tenantId, String period, UsageKind kind) {
        List<Integer> rows = jdbc.queryForList("""
            SELECT used FROM tenant_monthly_usage
            WHERE tenant_id = :tenantId AND period = :period AND kind = :kind
            """, key(tenantId, period, kind), Integer.class);
        return rows.isEmpty() ? 0 : rows.get(0);
    }

    private static MapSqlParameterSource key(UUID tenantId, String period, UsageKind kind) {
        return new MapSqlParameterSource()
            .addValue("tenantId", tenantId)
            .addValue("period", period)
            .addValue("kind", kind.name());
    }
}
```

- [ ] **Step 7: `PlanUsageQuotaAdapter`**

```java
package com.vetos.modules.platformadmin.infrastructure.adapter;

import com.vetos.modules.platformadmin.domain.Plan;
import com.vetos.modules.platformadmin.domain.PlanRepository;
import com.vetos.modules.platformadmin.infrastructure.persistence.MonthlyUsageStore;
import com.vetos.modules.tenant.domain.TenantAdminPort;
import com.vetos.platform.quota.MonthlyQuotaExceededException;
import com.vetos.platform.quota.QuotaProperties;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import com.vetos.platform.quota.UsageSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;

/**
 * Paket kotasi cozumu (spec 2026-10-15 §2.4) -- sira PlanFeatureAccessAdapter ile ayni:
 * kisitlama kapali -> say ama engelleme; abonelik yok -> 0; TRIAL -> yml; plan -> sutun
 * (null = sinirsiz); plan satiri yok -> 0. Onbellek yok: admin degeri degistirince sonraki istek gorur.
 */
@Component
@Slf4j
class PlanUsageQuotaAdapter implements UsageQuotaPort {

    static final ZoneId ZONE = ZoneId.of("Europe/Istanbul");
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM");

    private final TenantAdminPort tenantAdminPort;
    private final PlanRepository planRepository;
    private final MonthlyUsageStore store;
    private final QuotaProperties properties;
    private final boolean enforcementEnabled;
    private final Clock clock;

    @Autowired
    PlanUsageQuotaAdapter(
        TenantAdminPort tenantAdminPort,
        PlanRepository planRepository,
        MonthlyUsageStore store,
        QuotaProperties properties,
        @Value("${features.enforcement.enabled:false}") boolean enforcementEnabled
    ) {
        this(tenantAdminPort, planRepository, store, properties, enforcementEnabled, Clock.system(ZONE));
    }

    PlanUsageQuotaAdapter(
        TenantAdminPort tenantAdminPort,
        PlanRepository planRepository,
        MonthlyUsageStore store,
        QuotaProperties properties,
        boolean enforcementEnabled,
        Clock clock
    ) {
        this.tenantAdminPort = tenantAdminPort;
        this.planRepository = planRepository;
        this.store = store;
        this.properties = properties;
        this.enforcementEnabled = enforcementEnabled;
        this.clock = clock;
    }

    @Override
    public void reserve(UUID tenantId, UsageKind kind, int amount) {
        if (amount <= 0) {
            return;
        }
        String period = currentPeriod();
        if (!enforcementEnabled) {
            store.increment(tenantId, period, kind, amount);
            return;
        }
        Integer quota = resolveQuota(tenantId, kind);
        if (quota == null) {
            store.increment(tenantId, period, kind, amount);
            return;
        }
        if (!store.tryIncrement(tenantId, period, kind, amount, quota)) {
            throw new MonthlyQuotaExceededException(kind, store.used(tenantId, period, kind), quota, amount);
        }
    }

    @Override
    public void recordUnconditional(UUID tenantId, UsageKind kind, int amount) {
        if (amount <= 0) {
            return;
        }
        store.increment(tenantId, currentPeriod(), kind, amount);
    }

    @Override
    public void release(UUID tenantId, UsageKind kind, int amount) {
        if (amount <= 0) {
            return;
        }
        store.decrement(tenantId, currentPeriod(), kind, amount);
    }

    @Override
    public UsageSnapshot currentUsage(UUID tenantId) {
        String period = currentPeriod();
        return new UsageSnapshot(
            period,
            enforcementEnabled,
            new UsageSnapshot.Item(store.used(tenantId, period, UsageKind.AI), resolveQuota(tenantId, UsageKind.AI)),
            new UsageSnapshot.Item(store.used(tenantId, period, UsageKind.MESSAGE), resolveQuota(tenantId, UsageKind.MESSAGE))
        );
    }

    /** null = sinirsiz. Optional.map KULLANMA: null kota bos Optional'a donup "plan yok -> 0"a duser. */
    private Integer resolveQuota(UUID tenantId, UsageKind kind) {
        Optional<String> planCode = tenantAdminPort.findPlanCode(tenantId);
        if (planCode.isEmpty()) {
            log.warn("Abonelik bulunamadi, kota 0: tenantId={}", tenantId);
            return 0;
        }
        if (PlanFeatureAccessAdapter.TRIAL_PLAN_CODE.equals(planCode.get())) {
            return kind == UsageKind.AI ? properties.trial().ai() : properties.trial().message();
        }
        Optional<Plan> plan = planRepository.findByCode(planCode.get());
        if (plan.isEmpty()) {
            log.warn("Plan bulunamadi, kota 0: tenantId={}, planCode={}", tenantId, planCode.get());
            return 0;
        }
        return kind == UsageKind.AI ? plan.get().getMonthlyAiQuota() : plan.get().getMonthlyMessageQuota();
    }

    private String currentPeriod() {
        return YearMonth.now(clock.withZone(ZONE)).format(PERIOD_FORMAT);
    }
}
```

- [ ] **Step 8: Handler, modül izni, yml**

`GlobalExceptionHandler`'a (`handleRateLimited`'in altına):

```java
    @ExceptionHandler(MonthlyQuotaExceededException.class)
    public ResponseEntity<ErrorResponse> handleMonthlyQuota(MonthlyQuotaExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), request.getRequestURI()));
    }
```

`platformadmin/package-info.java` `allowedDependencies` listesine `"platform::quota"` ekle.

`application.yml` `vetly:` köküne (Task 1'de eklenen `rate-limit:` kardeşi olarak):

```yaml
  # Paket kotalari: degerler admin panelinden (Paket Yonetimi). TRIAL'in plans satiri
  # olmadigi icin deneme kotalari burada. Env: VETLY_QUOTA_TRIAL_AI / VETLY_QUOTA_TRIAL_MESSAGE.
  quota:
    trial:
      ai: 30
      message: 100
```

- [ ] **Step 9: Testleri çalıştır**

Run: `./mvnw -q test -Dtest='MonthlyUsageStoreTest,PlanUsageQuotaAdapterTest,PlanTest,MonthlyQuotaExceededExceptionTest,GlobalExceptionHandlerRateLimitTest,PlanFeatureAccessAdapterTest,ApplicationModulesTest'`
Expected: PASS (Flyway V80'i canlı dev DB'ye uygular — eklemeli değişiklik, çalışan backend etkilenmez).

---

### Task 4: AI kotası — üç üretim use case'i

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/ai/application/GenerateSoapDraftUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/application/GenerateDiagnosisSuggestionUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/application/GenerateTreatmentRecommendationUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/api/AiController.java` (SOAP ucu kiracıyı geçirir)
- Modify: `backend/src/main/java/com/vetos/modules/ai/package-info.java` (`"platform::quota"`)
- Test: `backend/src/test/java/com/vetos/modules/ai/application/GenerateSoapDraftUseCaseTest.java` (yoksa oluştur)
- Test: `backend/src/test/java/com/vetos/modules/ai/application/GenerateDiagnosisSuggestionUseCaseTest.java` (genişlet + yeni ctor)
- Test: `backend/src/test/java/com/vetos/modules/ai/application/GenerateTreatmentRecommendationUseCaseTest.java` (genişlet + yeni ctor)

**Interfaces:**
- Consumes: `UsageQuotaPort#reserve/#release`, `UsageKind.AI`, `MonthlyQuotaExceededException` (Task 3).
- Produces: `GenerateSoapDraftUseCase#execute(UUID tenantId, String transcriptText): SoapDraft` (imza değişti); Diagnosis/Treatment use case constructor'larına son parametre olarak `UsageQuotaPort usageQuotaPort` (`@Value` parametresinden SONRA).

Kural (spec §2.4): girdi doğrulaması (Assessment/Subjective-Objective boş) `reserve`'den ÖNCE; `reserve(tenant, AI, 1)` model çağrısından önce; model istisna fırlatırsa `release` + istisnayı aynen yay; `modelConnected() == false` ise `release` + taslağı aynen döndür; kota doluysa model ÇAĞRILMAZ.

- [ ] **Step 1: Başarısız testleri yaz**

`GenerateSoapDraftUseCaseTest.java`:

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AudioTranscript;
import com.vetos.modules.ai.domain.SoapDraft;
import com.vetos.modules.ai.domain.SoapGenerationPort;
import com.vetos.platform.quota.MonthlyQuotaExceededException;
import com.vetos.platform.quota.UsageKind;
import com.vetos.platform.quota.UsageQuotaPort;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GenerateSoapDraftUseCaseTest {

    private static final UUID TENANT = UUID.randomUUID();
    private final SoapGenerationPort port = mock(SoapGenerationPort.class);
    private final UsageQuotaPort quota = mock(UsageQuotaPort.class);
    private final GenerateSoapDraftUseCase useCase = new GenerateSoapDraftUseCase(port, quota);

    @Test
    void should_reserveBeforeCalling_and_keep_when_modelConnected() {
        SoapDraft draft = new SoapDraft("s", "o", "a", "p", true);
        when(port.generate(any(AudioTranscript.class))).thenReturn(draft);

        assertThat(useCase.execute(TENANT, "metin")).isEqualTo(draft);

        InOrder order = inOrder(quota, port);
        order.verify(quota).reserve(TENANT, UsageKind.AI, 1);
        order.verify(port).generate(any());
        verify(quota, never()).release(any(), any(), anyInt());
    }

    @Test
    void should_release_when_modelNotConnected() {
        SoapDraft fallback = new SoapDraft("metin", "", "", "", false);
        when(port.generate(any())).thenReturn(fallback);

        assertThat(useCase.execute(TENANT, "metin")).isEqualTo(fallback);

        verify(quota).release(TENANT, UsageKind.AI, 1);
    }

    @Test
    void should_release_and_rethrow_when_modelThrows() {
        when(port.generate(any())).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> useCase.execute(TENANT, "metin")).hasMessage("boom");

        verify(quota).release(TENANT, UsageKind.AI, 1);
    }

    @Test
    void should_notCallModel_when_quotaExhausted() {
        doThrow(new MonthlyQuotaExceededException(UsageKind.AI, 30, 30, 1)).when(quota).reserve(TENANT, UsageKind.AI, 1);

        assertThatThrownBy(() -> useCase.execute(TENANT, "metin")).isInstanceOf(MonthlyQuotaExceededException.class);

        verifyNoInteractions(port);
    }
}
```

Diagnosis ve Treatment testlerine aynı dört senaryoyu ekle (mevcut test kurulumlarındaki mock'ları kullanarak; constructor'a son parametre olarak `quota` mock'u ver). Ek olarak her birinde doğrulama senaryosu:

```java
    @Test
    void should_notReserve_when_validationFails() {
        // mevcut "Assessment bos" (Treatment) / "Subjective+Objective bos" (Diagnosis) senaryosunun kurulumu
        // ... execute -> beklenen domain istisnasi
        verifyNoInteractions(quota);
    }
```

(Mevcut testte bu doğrulama senaryosu zaten varsa, ona `verifyNoInteractions(quota);` satırını eklemek yeterli.) `modelConnected=false` taslak: `new DiagnosisSuggestionDraft("", false, "v")` / `new TreatmentRecommendationDraft("", false, "v")`. Not: `modelConnected=false` olsa da AI iş kaydı (`recordAiJobUseCase`) bugünkü gibi yazılır — davranışı değiştirme.

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `./mvnw -q test -Dtest='GenerateSoapDraftUseCaseTest,GenerateDiagnosisSuggestionUseCaseTest,GenerateTreatmentRecommendationUseCaseTest'`
Expected: derleme hatası (constructor/imza).

- [ ] **Step 3: Use case'leri güncelle**

`GenerateSoapDraftUseCase`:

```java
@Service
@RequiredArgsConstructor
public class GenerateSoapDraftUseCase {

    private final SoapGenerationPort soapGenerationPort;
    private final UsageQuotaPort usageQuotaPort;

    public SoapDraft execute(UUID tenantId, String transcriptText) {
        usageQuotaPort.reserve(tenantId, UsageKind.AI, 1);
        SoapDraft draft;
        try {
            draft = soapGenerationPort.generate(new AudioTranscript(transcriptText));
        } catch (RuntimeException e) {
            usageQuotaPort.release(tenantId, UsageKind.AI, 1);
            throw e;
        }
        if (!draft.modelConnected()) {
            // Adaptor hatayi yutup bos taslak dondurdu -- klinik bosuna hak yemesin.
            usageQuotaPort.release(tenantId, UsageKind.AI, 1);
        }
        return draft;
    }
}
```

`GenerateDiagnosisSuggestionUseCase`: constructor'a `UsageQuotaPort usageQuotaPort` (son parametre) + alan; `execute` içinde `input` oluşturulduktan sonra, `diagnosisSuggestionPort.generate(input)` satırının yerine:

```java
        usageQuotaPort.reserve(command.tenantId(), UsageKind.AI, 1);
        DiagnosisSuggestionDraft draft;
        try {
            draft = diagnosisSuggestionPort.generate(input);
        } catch (RuntimeException e) {
            usageQuotaPort.release(command.tenantId(), UsageKind.AI, 1);
            throw e;
        }
        if (!draft.modelConnected()) {
            usageQuotaPort.release(command.tenantId(), UsageKind.AI, 1);
        }
```

`GenerateTreatmentRecommendationUseCase`: aynı desen (`treatmentRecommendationPort.generate(input)` yerine, `TreatmentRecommendationDraft` tipiyle).

`AiController.generateSoapDraft`:

```java
    @PostMapping("/soap-drafts")
    public SoapDraftResponse generateSoapDraft(
        @RequestBody @Valid GenerateSoapDraftRequest request,
        @AuthenticationPrincipal AuthenticatedStaffUser principal
    ) {
        return SoapDraftResponse.from(generateSoapDraftUseCase.execute(principal.tenantId(), request.transcript()));
    }
```

`ai/package-info.java` `allowedDependencies`'e `"platform::quota"` ekle. `GenerateSoapDraftUseCase.execute(String)` başka yerden çağrılıyorsa (`grep -rn "generateSoapDraftUseCase.execute\|GenerateSoapDraftUseCase" backend/src`) hepsini yeni imzaya güncelle.

- [ ] **Step 4: Testleri çalıştır**

Run: `./mvnw -q test -Dtest='GenerateSoapDraftUseCaseTest,GenerateDiagnosisSuggestionUseCaseTest,GenerateTreatmentRecommendationUseCaseTest,ApplicationModulesTest'`
Expected: PASS.

---

### Task 5: Kampanya sınırları ve mesaj kotası

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/notification/api/dto/SendCampaignRequest.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/application/SendCampaignUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/application/QueueNotificationUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/package-info.java` (`"platform::quota"`, `"platform::ratelimit"`)
- Test: `backend/src/test/java/com/vetos/modules/notification/application/SendCampaignUseCaseTest.java` (yoksa oluştur; varsa genişlet)
- Test: `backend/src/test/java/com/vetos/modules/notification/application/QueueNotificationUseCaseTest.java` (yoksa oluştur; varsa genişlet)
- Test: `backend/src/test/java/com/vetos/modules/notification/api/dto/SendCampaignRequestValidationTest.java`

**Interfaces:**
- Consumes: `TenantRateLimiter#consumeOrThrow/#refund`, `TenantPool.CAMPAIGN_RECIPIENTS`, `RateLimitExceededException` (Task 1); `UsageQuotaPort#reserve/#release/#recordUnconditional`, `UsageKind.MESSAGE`, `MonthlyQuotaExceededException` (Task 3).
- Produces: `SendCampaignRequest.MAX_RECIPIENTS = 1000`; `SendCampaignUseCase(QueueNotificationUseCase, TenantRateLimiter, UsageQuotaPort)`; `QueueNotificationUseCase(NotificationLogRepository, NotificationSendExecutor, UsageQuotaPort)`.

Kural (spec §1.5, §2.4): uygun alıcı = telefonu dolu VE kanal izni olan (mevcut atlama kuralları). N = uygun alıcı sayısı. N = 0 → hiçbir sınır/kota harcanmaz. Sıra: saatlik jeton (`consumeOrThrow`) → kota (`reserve`; reddederse saatlik jetonları `refund` et, istisnayı yay) → kuyruğa alma (istisna olursa `release(MESSAGE, N)`, saatlik jeton iade YOK). Kampanya mesajı `QueueNotificationUseCase`'te tekrar sayılmaz; diğer üç tür her zaman `recordUnconditional(MESSAGE, 1)`.

- [ ] **Step 1: Başarısız testleri yaz**

`SendCampaignRequestValidationTest.java`:

```java
package com.vetos.modules.notification.api.dto;

import com.vetos.modules.notification.domain.NotificationChannel;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SendCampaignRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private SendCampaignRequest withRecipients(int count) {
        List<CampaignRecipientRequest> recipients = Collections.nCopies(count, null);
        return new SendCampaignRequest(NotificationChannel.SMS, "Merhaba", recipients);
    }

    @Test
    void should_accept_maxRecipients() {
        assertThat(validator.validateProperty(withRecipients(SendCampaignRequest.MAX_RECIPIENTS), "recipients")).isEmpty();
    }

    @Test
    void should_reject_moreThanMaxRecipients() {
        var violations = validator.validateProperty(withRecipients(SendCampaignRequest.MAX_RECIPIENTS + 1), "recipients");
        assertThat(violations).singleElement()
            .satisfies(v -> assertThat(v.getMessage()).isEqualTo("Tek seferde en fazla 1000 alıcıya gönderilebilir"));
    }

    @Test
    void should_capAtOneThousand() {
        assertThat(SendCampaignRequest.MAX_RECIPIENTS).isEqualTo(1000);
    }
}
```

(`validateProperty` yalnızca `recipients` üzerindeki `@NotNull`/`@Size` kısıtlarını değerlendirir; `null` elemanlar `@Valid` cascade olmadığı sürece sorun değildir. `CampaignRecipientRequest` elemanlarında `@Valid` cascade varsa `Collections.nCopies` yerine geçerli bir örnek kullan.)

`SendCampaignUseCaseTest.java` — mevcut `CampaignRecipient` record'unun alan sırasını `modules/notification/application` (veya domain) içinden oku; testte bir yardımcı `eligible(i)` (telefon dolu, `smsConsent=true`) ve `noPhone()` üret. Senaryolar:

```java
    @Test
    void should_consumeAndReserve_onlyForEligibleRecipients() {
        // 3 uygun + 1 telefonsuz + 1 izinsiz
        var summary = useCase.execute(TENANT, NotificationChannel.SMS, "Merhaba {ad}", recipients);

        InOrder order = inOrder(limiter, quota, queue);
        order.verify(limiter).consumeOrThrow(TenantPool.CAMPAIGN_RECIPIENTS, TENANT, 3);
        order.verify(quota).reserve(TENANT, UsageKind.MESSAGE, 3);
        order.verify(queue, times(3)).execute(eq(TENANT), any(), isNull(), eq(NotificationChannel.SMS),
            eq(NotificationType.CAMPAIGN_MESSAGE), any(), any(), isNull(), any());
        assertThat(summary.queued()).isEqualTo(3);
    }

    @Test
    void should_touchNothing_when_noEligibleRecipients() {
        useCase.execute(TENANT, NotificationChannel.SMS, "x", List.of(noPhone()));
        verifyNoInteractions(limiter, quota, queue);
    }

    @Test
    void should_notReserveQuota_when_hourlyLimitRejects() {
        doThrow(new RateLimitExceededException(120)).when(limiter).consumeOrThrow(TenantPool.CAMPAIGN_RECIPIENTS, TENANT, 3);
        assertThatThrownBy(() -> useCase.execute(TENANT, NotificationChannel.SMS, "x", threeEligible))
            .isInstanceOf(RateLimitExceededException.class);
        verifyNoInteractions(quota, queue);
    }

    @Test
    void should_refundHourlyTokens_when_quotaRejects() {
        doThrow(new MonthlyQuotaExceededException(UsageKind.MESSAGE, 900, 1000, 3)).when(quota).reserve(TENANT, UsageKind.MESSAGE, 3);
        assertThatThrownBy(() -> useCase.execute(TENANT, NotificationChannel.SMS, "x", threeEligible))
            .isInstanceOf(MonthlyQuotaExceededException.class);
        verify(limiter).refund(TenantPool.CAMPAIGN_RECIPIENTS, TENANT, 3);
        verifyNoInteractions(queue);
    }

    @Test
    void should_releaseQuota_when_queueingFails() {
        when(queue.execute(any(), any(), any(), any(), any(), any(), any(), any(), any()))
            .thenReturn(UUID.randomUUID())
            .thenThrow(new IllegalStateException("db"));
        assertThatThrownBy(() -> useCase.execute(TENANT, NotificationChannel.SMS, "x", threeEligible))
            .hasMessage("db");
        verify(quota).release(TENANT, UsageKind.MESSAGE, 3);
        verify(limiter, never()).refund(any(), any(), anyLong());
    }

    @Test
    void should_keepSkipCountsAsBefore() {
        var summary = useCase.execute(TENANT, NotificationChannel.SMS, "x", List.of(eligible(1), noPhone(), noConsent()));
        assertThat(summary.queued()).isEqualTo(1);
        assertThat(summary.skippedNoPhone()).isEqualTo(1);
        assertThat(summary.skippedNoConsent()).isEqualTo(1);
    }
```

(`limiter` = `mock(TenantRateLimiter.class)`, `quota` = `mock(UsageQuotaPort.class)`, `queue` = `mock(QueueNotificationUseCase.class)`, `useCase = new SendCampaignUseCase(queue, limiter, quota)`.)

`QueueNotificationUseCaseTest.java`:

```java
    @ParameterizedTest
    @EnumSource(value = NotificationType.class, names = {"APPOINTMENT_CONFIRMATION", "APPOINTMENT_REMINDER", "VACCINATION_REMINDER"})
    void should_countAutomaticMessages_unconditionally(NotificationType type) {
        useCase.execute(TENANT, UUID.randomUUID(), null, NotificationChannel.SMS, type, "05xx", "mesaj", null, "Ali");
        verify(quota).recordUnconditional(TENANT, UsageKind.MESSAGE, 1);
    }

    @Test
    void should_notCountCampaignMessages_again() {
        useCase.execute(TENANT, UUID.randomUUID(), null, NotificationChannel.SMS, NotificationType.CAMPAIGN_MESSAGE, "05xx", "m", null, "Ali");
        verifyNoInteractions(quota);
    }
```

(Kurulum: `NotificationLogRepository` mock'u `save(any())` için argümanı döndürsün ve id'si olan bir log versin — mevcut test varsa onun kurulumunu kullan; `NotificationSendExecutor` mock. Transaction senkronizasyonu yokken executor doğrudan çağrılır — beklenen.)

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `./mvnw -q test -Dtest='SendCampaignRequestValidationTest,SendCampaignUseCaseTest,QueueNotificationUseCaseTest'`
Expected: derleme hatası.

- [ ] **Step 3: Uygula**

`SendCampaignRequest`:

```java
public record SendCampaignRequest(
    @NotNull NotificationChannel channel,
    @NotBlank String messageBody,
    @NotNull @Size(max = SendCampaignRequest.MAX_RECIPIENTS, message = "Tek seferde en fazla 1000 alıcıya gönderilebilir")
    List<CampaignRecipientRequest> recipients
) {
    /** Kodda sabit: anotasyon degeri yml'den okunamaz (spec 2026-10-15 §1.5). */
    public static final int MAX_RECIPIENTS = 1000;
}
```

`SendCampaignUseCase.execute` (alanlar: `queueNotificationUseCase`, `tenantRateLimiter`, `usageQuotaPort`; `@RequiredArgsConstructor` sırası alan sırasıdır — yukarıdaki constructor sırasına uy):

```java
    @Transactional
    public CampaignSendSummary execute(UUID tenantId, NotificationChannel channel, String messageTemplate, List<CampaignRecipient> recipients) {
        int skippedNoPhone = 0;
        int skippedNoConsent = 0;
        List<CampaignRecipient> eligible = new ArrayList<>();
        for (CampaignRecipient recipient : recipients) {
            if (recipient.phone() == null || recipient.phone().isBlank()) {
                skippedNoPhone++;
                continue;
            }
            boolean consented = channel == NotificationChannel.SMS ? recipient.smsConsent() : recipient.whatsappConsent();
            if (!consented) {
                skippedNoConsent++;
                continue;
            }
            eligible.add(recipient);
        }

        int n = eligible.size();
        if (n > 0) {
            tenantRateLimiter.consumeOrThrow(TenantPool.CAMPAIGN_RECIPIENTS, tenantId, n);
            try {
                usageQuotaPort.reserve(tenantId, UsageKind.MESSAGE, n);
            } catch (MonthlyQuotaExceededException e) {
                tenantRateLimiter.refund(TenantPool.CAMPAIGN_RECIPIENTS, tenantId, n);
                throw e;
            }
            try {
                for (CampaignRecipient recipient : eligible) {
                    queueNotificationUseCase.execute(
                        tenantId, recipient.ownerId(), null, channel, NotificationType.CAMPAIGN_MESSAGE,
                        recipient.phone(), substitute(messageTemplate, recipient.variables()), null, recipient.label()
                    );
                }
            } catch (RuntimeException e) {
                // Kota REQUIRES_NEW ile yazildi; bu islem geri alinacagi icin ayrilan hak elle iade edilir.
                usageQuotaPort.release(tenantId, UsageKind.MESSAGE, n);
                throw e;
            }
        }
        return new CampaignSendSummary(n, skippedNoPhone, skippedNoConsent);
    }
```

`QueueNotificationUseCase`: alan `private final UsageQuotaPort usageQuotaPort;` (son alan) ve `save` satırından hemen sonra:

```java
        if (notificationType != NotificationType.CAMPAIGN_MESSAGE) {
            // Otomatik mesajlar asla engellenmez ama kotaya sayilir (kampanya SendCampaignUseCase'te ayrildi).
            usageQuotaPort.recordUnconditional(tenantId, UsageKind.MESSAGE, 1);
        }
```

`notification/package-info.java` `allowedDependencies`'e `"platform::quota", "platform::ratelimit"` ekle.

- [ ] **Step 4: Testleri çalıştır**

Run: `./mvnw -q test -Dtest='SendCampaignRequestValidationTest,SendCampaignUseCaseTest,QueueNotificationUseCaseTest,ApplicationModulesTest' && ./mvnw -q test -Dtest='*Notification*,*Campaign*,*Reminder*'`
Expected: PASS (mevcut bildirim testleri yeni constructor parametresi yüzünden kırılırsa mock ekleyerek düzelt — davranış değişikliği yapma).

---

### Task 6: Uçlar — paket kotası alanları, deneme kotaları, kullanım uçları

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/dto/UpdatePlanRequest.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/application/dto/UpdatePlanCommand.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/application/UpdatePlanUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/dto/PlanResponse.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/dto/FeatureOptionsResponse.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/PlanController.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/api/PlatformAdminTenantsController.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/api/SubscriptionsController.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/application/UpdatePlanUseCaseTest.java` (genişlet)
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/api/dto/UpdatePlanRequestValidationTest.java`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/api/dto/FeatureOptionsResponseTest.java` (varsa genişlet)
- Test: `backend/src/test/java/com/vetos/UsageEndpointsSecurityTest.java`

**Interfaces:**
- Consumes: `Plan#updateQuotas`, `Plan#getMonthlyAiQuota/#getMonthlyMessageQuota`, `QuotaProperties`, `UsageQuotaPort#currentUsage`, `UsageSnapshot` (Task 3).
- Produces (JSON sözleşmesi, Task 7 kullanır):
  - `PUT /api/v1/platform-admin/plans/{id}` gövdesine `monthlyAiQuota: number|null`, `monthlyMessageQuota: number|null` (negatif → 400).
  - `GET /api/v1/platform-admin/plans` her plana `monthlyAiQuota`, `monthlyMessageQuota`.
  - `GET /api/v1/platform-admin/plans/feature-options` → ek alan `trialQuotas: { ai: number, message: number }`.
  - `GET /api/v1/platform-admin/tenants/{id}/usage` → `UsageSnapshot` JSON: `{ period, enforced, ai: { used, quota }, message: { used, quota } }`.
  - `GET /api/v1/subscriptions/usage` (yalnız ADMIN) → aynı `UsageSnapshot` JSON.

- [ ] **Step 1: Başarısız testleri yaz**

`UpdatePlanRequestValidationTest.java`:

```java
package com.vetos.modules.platformadmin.api.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UpdatePlanRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private UpdatePlanRequest request(Integer ai, Integer message) {
        return new UpdatePlanRequest("Pro", BigDecimal.TEN, null, null, null, null, List.of(), Set.of(), true, ai, message);
    }

    @Test
    void should_allowNullAndZero() {
        assertThat(validator.validate(request(null, null))).isEmpty();
        assertThat(validator.validate(request(0, 0))).isEmpty();
    }

    @Test
    void should_rejectNegative() {
        assertThat(validator.validate(request(-1, null))).hasSize(1);
        assertThat(validator.validate(request(null, -1))).hasSize(1);
    }
}
```

`UpdatePlanUseCaseTest`'e: mevcut "günceller" testinin kurulumunu kullanarak komuta `monthlyAiQuota=100`, `monthlyMessageQuota=null` ver ve kaydedilen planda `getMonthlyAiQuota()==100`, `getMonthlyMessageQuota()==null` olduğunu doğrula (mevcut komut oluşturma yerlerine iki yeni argümanı ekle).

`FeatureOptionsResponseTest` (yoksa oluştur):

```java
    @Test
    void should_includeTrialQuotas() {
        var response = FeatureOptionsResponse.of(false, new QuotaProperties.Trial(30, 100));
        assertThat(response.trialQuotas()).isEqualTo(new FeatureOptionsResponse.TrialQuotas(30, 100));
    }
```

`UsageEndpointsSecurityTest.java` (canlı Postgres; port mock'lanır):

```java
package com.vetos;

import com.vetos.platform.quota.UsageQuotaPort;
import com.vetos.platform.quota.UsageSnapshot;
import com.vetos.platform.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Canli docker-compose Postgres gerekir. */
@SpringBootTest
@AutoConfigureMockMvc
class UsageEndpointsSecurityTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private UsageQuotaPort usageQuotaPort;

    private String bearer(UUID tenantId, String role) {
        return "Bearer " + jwtTokenProvider.generateToken(UUID.randomUUID(), tenantId, List.of(), role);
    }

    @Test
    void should_returnUsage_forClinicAdmin() throws Exception {
        UUID tenant = UUID.randomUUID();
        when(usageQuotaPort.currentUsage(tenant)).thenReturn(new UsageSnapshot(
            "2026-10", true, new UsageSnapshot.Item(34, 100), new UsageSnapshot.Item(820, null)));

        mockMvc.perform(get("/api/v1/subscriptions/usage").header("Authorization", bearer(tenant, "ADMIN")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.period").value("2026-10"))
            .andExpect(jsonPath("$.enforced").value(true))
            .andExpect(jsonPath("$.ai.used").value(34))
            .andExpect(jsonPath("$.ai.quota").value(100))
            .andExpect(jsonPath("$.message.quota").isEmpty());
    }

    @Test
    void should_forbidUsage_forVet() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/usage").header("Authorization", bearer(UUID.randomUUID(), "VET")))
            .andExpect(status().isForbidden());
    }
}
```

(Platform admin `/tenants/{id}/usage` ucu sınıf düzeyinde `@PreAuthorize("hasRole('PLATFORM_ADMIN')")` taşıyan mevcut controller'a eklendiği için ayrı güvenlik testi gerekmez; personel JWT'si o zincire hiç girmez.)

- [ ] **Step 2: Başarısız olduğunu gör**

Run: `./mvnw -q test -Dtest='UpdatePlanRequestValidationTest,UpdatePlanUseCaseTest,FeatureOptionsResponseTest'`
Expected: derleme hatası.

- [ ] **Step 3: Uygula**

`UpdatePlanRequest` sonuna (`boolean active`'ten sonra):

```java
    boolean active,
    @PositiveOrZero Integer monthlyAiQuota,
    @PositiveOrZero Integer monthlyMessageQuota
```

`UpdatePlanCommand` sonuna `Integer monthlyAiQuota, Integer monthlyMessageQuota`. `PlanController.update` komutu oluştururken `request.monthlyAiQuota(), request.monthlyMessageQuota()` ekle. `UpdatePlanUseCase` içinde `plan.updateDetails(...)`'ten sonra:

```java
        plan.updateQuotas(command.monthlyAiQuota(), command.monthlyMessageQuota());
```

`PlanResponse`'a `Integer monthlyAiQuota, Integer monthlyMessageQuota` (`active`'ten sonra) ve `from` içinde `plan.getMonthlyAiQuota(), plan.getMonthlyMessageQuota()`.

`FeatureOptionsResponse`:

```java
public record FeatureOptionsResponse(boolean enforcementActive, List<Option> options, TrialQuotas trialQuotas) {

    public record Option(String code, String label) {}

    /** Salt-okunur bilgi: deneme paketinin kotalari sunucu ayarindan (vetly.quota.trial). */
    public record TrialQuotas(int ai, int message) {}

    public static FeatureOptionsResponse of(boolean enforcementActive, QuotaProperties.Trial trial) {
        List<Option> options = Arrays.stream(Feature.values())
            .map(f -> new Option(f.name(), f.label()))
            .toList();
        return new FeatureOptionsResponse(enforcementActive, options, new TrialQuotas(trial.ai(), trial.message()));
    }
}
```

`PlanController`: alan `private final QuotaProperties quotaProperties;` ve `featureOptions()` → `FeatureOptionsResponse.of(featureAccessPort.enforcementActive(), quotaProperties.trial())`. (`FeatureOptionsResponse.of(boolean)`'ı başka çağıran varsa güncelle.)

`PlatformAdminTenantsController`: alan `private final UsageQuotaPort usageQuotaPort;` ve:

```java
    /** Tenant Detayi -- "Bu ayki kullanim" kutusu (spec 2026-10-15 §3.2). */
    @GetMapping("/{id}/usage")
    public UsageSnapshot usage(@PathVariable UUID id) {
        return usageQuotaPort.currentUsage(id);
    }
```

`SubscriptionsController`: alan `private final UsageQuotaPort usageQuotaPort;` ve:

```java
    /** Klinik yoneticisi: bu ayki AI / mesaj kullanimi ve paket kotalari. */
    @GetMapping("/usage")
    @PreAuthorize("hasRole('ADMIN')")
    public UsageSnapshot usage() {
        return usageQuotaPort.currentUsage(TenantContext.current());
    }
```

- [ ] **Step 4: Testleri çalıştır**

Run: `./mvnw -q test -Dtest='UpdatePlanRequestValidationTest,UpdatePlanUseCaseTest,FeatureOptionsResponseTest,UsageEndpointsSecurityTest,ApplicationModulesTest'`
Expected: PASS.

---

### Task 7: Ön yüz — paket formu, deneme kotaları, kullanım kutuları, kampanya ön kontrolü

**Files:**
- Modify: `frontend/src/api/platformAdminApi.ts`
- Modify: `frontend/src/api/subscriptionApi.ts`
- Create: `frontend/src/components/usage/UsageSummary.tsx`
- Create: `frontend/src/components/usage/UsageSummary.module.css`
- Modify: `frontend/src/pages/platform-admin/PlanManagementPage.tsx`
- Modify: `frontend/src/pages/platform-admin/TenantDetailPage.tsx`
- Modify: `frontend/src/pages/settings/SubscriptionPanel.tsx`
- Modify: `frontend/src/pages/sms-whatsapp/CampaignTab.tsx`

**Interfaces:**
- Consumes: Task 6'daki JSON sözleşmesi.
- Produces: `UsageSnapshot` TS tipi (`subscriptionApi.ts`'te export), `<UsageSummary usage={...} />` bileşeni.

Ön yüzde birim test altyapısı yok (yalnız Playwright e2e). Doğrulama: `npm run build` (tsc + vite) temiz geçmeli. Yeni test altyapısı KURMA.

- [ ] **Step 1: Tipler ve API çağrıları**

`subscriptionApi.ts`:

```ts
export interface UsageItem {
  used: number;
  quota: number | null;
}

export interface UsageSnapshot {
  period: string;
  enforced: boolean;
  ai: UsageItem;
  message: UsageItem;
}
```

`subscriptionApi` nesnesine:

```ts
  getUsage: () => apiClient.get<UsageSnapshot>('/api/v1/subscriptions/usage'),
```

`platformAdminApi.ts`: `Plan` ve `UpdatePlanPayload`'a `monthlyAiQuota: number | null; monthlyMessageQuota: number | null;`; `FeatureOptions`'a `trialQuotas: { ai: number; message: number };`; nesneye:

```ts
  getTenantUsage: (id: string) =>
    platformAdminClient.get<UsageSnapshot>(`/api/v1/platform-admin/tenants/${id}/usage`),
```

(`UsageSnapshot`'ı `./subscriptionApi`'den import et.)

- [ ] **Step 2: `UsageSummary` bileşeni**

```tsx
import type { UsageItem, UsageSnapshot } from '../../api/subscriptionApi';
import styles from './UsageSummary.module.css';

const nf = new Intl.NumberFormat('tr-TR');

function Row({ label, item }: { label: string; item: UsageItem }) {
  if (item.quota === null) {
    return (
      <div className={styles.row}>
        <span className={styles.label}>{label}</span>
        <span>{nf.format(item.used)} (sınırsız)</span>
      </div>
    );
  }
  const over = item.used - item.quota;
  const pct = item.quota === 0 ? 100 : Math.min(100, Math.round((item.used / item.quota) * 100));
  return (
    <div className={styles.row}>
      <span className={styles.label}>{label}</span>
      <span>
        {nf.format(item.used)} / {nf.format(item.quota)}
        {over > 0 && <strong className={styles.over}> — aşım: {nf.format(over)}</strong>}
      </span>
      <div className={styles.bar} aria-hidden>
        <div className={over > 0 ? styles.fillOver : styles.fill} style={{ width: `${pct}%` }} />
      </div>
    </div>
  );
}

export function UsageSummary({ usage }: { usage: UsageSnapshot }) {
  return (
    <div className={styles.wrap}>
      <Row label="Bu ay AI" item={usage.ai} />
      <Row label="Bu ay mesaj (SMS + WhatsApp)" item={usage.message} />
      {!usage.enforced && <div className={styles.note}>Kotalar henüz uygulanmıyor; yalnızca sayılıyor.</div>}
    </div>
  );
}
```

`UsageSummary.module.css` (mevcut sayfalardaki CSS değişkenlerini kullan — `grep -rn "var(--" frontend/src/pages/settings/SubscriptionPanel.module.css` ile renk değişken adlarını al; aşağıdaki adlar yoksa oradakilerle değiştir):

```css
.wrap { display: flex; flex-direction: column; gap: 10px; }
.row { display: grid; grid-template-columns: 1fr auto; gap: 4px 12px; font-size: 13px; }
.label { color: var(--text-muted, #6b7280); }
.bar { grid-column: 1 / -1; height: 6px; border-radius: 3px; background: var(--border, #e5e7eb); overflow: hidden; }
.fill { height: 100%; background: var(--primary, #2563eb); }
.fillOver { height: 100%; background: var(--danger, #dc2626); }
.over { color: var(--danger, #dc2626); }
.note { font-size: 12px; color: var(--text-muted, #6b7280); }
```

- [ ] **Step 3: Paket Yönetimi formu**

`PlanFormState`'e `monthlyAiQuota: string; monthlyMessageQuota: string;`, `EMPTY_FORM`'a `''`, `openEdit`'te `plan.monthlyAiQuota === null ? '' : String(plan.monthlyAiQuota)` (mesaj için aynısı), `updatePlan` gövdesine:

```ts
          monthlyAiQuota: form.monthlyAiQuota === '' ? null : Number(form.monthlyAiQuota),
          monthlyMessageQuota: form.monthlyMessageQuota === '' ? null : Number(form.monthlyMessageQuota),
```

"Modül Erişimi" `FieldWrap`'inden hemen önce (yalnız `editingId` bloğunda):

```tsx
              <FieldWrap label="Aylık AI hakkı">
                <Input
                  type="number"
                  min={0}
                  step={1}
                  value={form.monthlyAiQuota}
                  onChange={(e) => setForm((f) => ({ ...f, monthlyAiQuota: e.target.value }))}
                  placeholder="Sınırsız"
                />
              </FieldWrap>
              <FieldWrap label="Aylık mesaj hakkı (SMS + WhatsApp)">
                <Input
                  type="number"
                  min={0}
                  step={1}
                  value={form.monthlyMessageQuota}
                  onChange={(e) => setForm((f) => ({ ...f, monthlyMessageQuota: e.target.value }))}
                  placeholder="Sınırsız"
                />
              </FieldWrap>
```

Sayfanın plan listesinin altına salt-okunur satır:

```tsx
      {featureOptions && (
        <div style={{ fontSize: 13, marginTop: 16, color: 'var(--text-muted, #6b7280)' }}>
          Deneme paketi kotaları: {featureOptions.trialQuotas.ai} AI / {featureOptions.trialQuotas.message} mesaj (sunucu ayarı)
        </div>
      )}
```

- [ ] **Step 4: Klinik Detayı ve Abonelik paneli**

`TenantDetailPage.tsx`: `integrationHealth`'in yüklendiği yerle aynı desende `const [usage, setUsage] = useState<UsageSnapshot | null>(null);` ve `platformAdminApi.getTenantUsage(tenantId).then(setUsage).catch(() => setUsage(null));`. "Entegrasyon Durumu" kartının hemen önüne aynı kart yapısıyla:

```tsx
          {usage && (
            <div className={styles.card}>
              <div className={styles.cardTitle}>Bu ayki kullanım ({usage.period})</div>
              <UsageSummary usage={usage} />
            </div>
          )}
```

(Kartın gerçek sarmalayıcı sınıf adını "Entegrasyon Durumu" kartından kopyala.)

`SubscriptionPanel.tsx`: abonelik yüklendikten sonra `subscriptionApi.getUsage()` çağır (hata olursa kutuyu gösterme — panelin geri kalanı etkilenmesin); plan kartının altına:

```tsx
      {usage && (
        <Card className={styles.card}>
          <div className={styles.planCode}>Bu ayki kullanım</div>
          <UsageSummary usage={usage} />
        </Card>
      )}
```

- [ ] **Step 5: Kampanya ön kontrolü**

`CampaignTab.tsx` gönderim fonksiyonunda, `campaignApi.sendCampaign` çağrısından önce:

```ts
    if (recipients.length > 1000) {
      setError('Tek seferde en fazla 1000 alıcıya gönderilebilir. Listeyi bölerek gönderin.');
      return;
    }
```

(Değişken adı farklıysa gönderilen alıcı dizisinin adını kullan.) Sunucudan gelen 429 mesajları zaten `errorMessageOf(err)` ile gösteriliyor — dokunma.

- [ ] **Step 6: Derle**

Run (frontend/): `npm run build`
Expected: hatasız biter.

---

### Task 8: Dokümantasyon, yol haritası ve tam doğrulama

**Files:**
- Modify: `docs/deployment.md`
- Modify: `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md`

- [ ] **Step 1: `docs/deployment.md`**

"İzleme (Faz 3)" bölümünden sonra yeni bölüm (Türkçe karakterli):

```markdown
## Rate limit ve paket kotaları (Faz 4)

**Nginx kontrolü (bir kez):** `/api` location'ında şu satır olmalı — yoksa ekleyip `sudo nginx -t && sudo systemctl reload nginx`:

    proxy_set_header X-Forwarded-For $remote_addr;

Doğrulama: yanlış şifreyle bir giriş dene, sonra `sudo journalctl -u vetly-backend -n 50` içinde kendi IP'ni gör (127.0.0.1 görüyorsan ayar eksik — tüm kullanıcılar tek IP limitini paylaşır).

**Koruma sınırları** (herkese aynı, `/etc/vetly/backend.env` ile değiştirilebilir):

| Ayar | Varsayılan | Env |
|---|---|---|
| Kiracı başına genel tavan | 600 / dk | `VETLY_RATE_LIMIT_TENANT_GENERAL_PER_MINUTE` |
| Kiracı başına AI üretimi | 10 / dk | `VETLY_RATE_LIMIT_TENANT_AI_PER_MINUTE` |
| Kiracı başına kampanya alıcısı | 2000 / saat | `VETLY_RATE_LIMIT_CAMPAIGN_RECIPIENTS_PER_HOUR` |
| Tek kampanyada alıcı | 1000 | kodda sabit |

**Paket kotaları:** Admin paneli → Paket Yönetimi → paketi düzenle → "Aylık AI hakkı", "Aylık mesaj hakkı". Boş = sınırsız. Deneme paketi: `VETLY_QUOTA_TRIAL_AI` (30), `VETLY_QUOTA_TRIAL_MESSAGE` (100).

**Önerilen açılış sırası:** `FEATURES_ENFORCEMENT_ENABLED=false` iken kotalar yalnızca sayılır. Birkaç hafta Klinik Detayı → "Bu ayki kullanım" kutusundan gerçek kullanımı izle, paketlere kota gir, sonra `FEATURES_ENFORCEMENT_ENABLED=true` yap (bu ayar Modül Erişimi işaretlerini de açar — onların da dolu olduğundan emin ol).
```

- [ ] **Step 2: Yol haritası**

Faz 4 başlığını `## Faz 4 — Rate limiting sertleştirme ✅` yap (varsa `← **SIRADAKİ**` kaldır), madde listesinin altına:

```markdown
- ✅ 2026-10-15: Caffeine IP önbelleği, 429 `Retry-After` + requestId, kiracı genel tavanı ve AI dakikalık sınırı, kampanya alıcı sınırları; ek olarak paket bazlı aylık AI/mesaj kotaları (spec `docs/superpowers/specs/2026-10-15-rate-limit-kota-faz4-design.md`). Çoklu instance (Redis) hâlâ kapsam dışı.
```

Faz 3b başlığına `← **SIRADAKİ**` ekle (Faz 3b yoksa Faz 5'e).

- [ ] **Step 3: Tam test paketi**

Run (backend/): `./mvnw -q test`
Expected: tüm testler yeşil (Faz 3 sonunda 645; yeni testlerle artmış olmalı). Kırmızı varsa düzelt, sayıları rapora yaz.

Run (frontend/): `npm run build`
Expected: hatasız.
