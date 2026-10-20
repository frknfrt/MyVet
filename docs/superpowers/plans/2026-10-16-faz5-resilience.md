# Faz 5 — Resilience Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** AI, iyzico ve FaturaEntegratör çağrılarına devre kesici, AI'ya eşzamanlılık sınırı ve yalnızca okuma çağrılarına kısa tekrar deneme eklemek; kopya HTTP istemcilerini ve tekrar deneme takvimlerini tekilleştirmek.

**Architecture:** `resilience4j-spring-boot3` 2.4.0 yml'den registry'leri kurar; kod programatik sarar (anotasyon/AOP yok). Ortak parçalar `com.vetos.platform.resilience` paketinde (hata sınıflandırıcı, durum logger'ı, `BackoffSchedule`). AI eşzamanlılık sınırı `ai` modülündeki `AiCallGate`'te; devre kesiciler adaptörlerin içinde gerçek HTTP çağrısını sarar.

**Tech Stack:** Spring Boot 3.5.16, Java 21, Resilience4j 2.4.0, Micrometer, RestClient, JUnit 5 + `com.sun.net.httpserver.HttpServer` sahte sunucu.

**Spec:** `docs/superpowers/specs/2026-10-16-resilience-faz5-design.md`

## Global Constraints

- **Commit YOK, push YOK.** `git add/commit/stash/checkout -- /reset/restore/clean` çalıştırma. Doğrudan `main`.
- Testler `backend/` içinden: `./mvnw -q test -Dtest=<Sınıf>`. `@SpringBootTest` canlı docker Postgres (5433) ister.
- Kod yorumları ASCII Türkçe; kullanıcıya giden mesajlar Türkçe karakterli, aşağıdaki metinlerle birebir.
- Kütüphane: `io.github.resilience4j:resilience4j-spring-boot3:2.4.0` (sürüm pom'da sabit). `spring-boot-starter-aop` EKLENMEZ — yalnız uygulama context'i onsuz açılmıyorsa eklenir ve Deviations'a yazılır. Anotasyon (`@CircuitBreaker`, `@Retry`, `@Bulkhead`) KULLANILMAZ.
- Devre kesici adları: `ai`, `payment`, `efatura`. Bulkhead: `ai` (`max-concurrent-calls: 4`, `max-wait-duration: 10s`). Retry: `payment-read`, `efatura-read` (3 deneme, 500 ms, çarpan 2).
- Devre kesici ortak ayarlar: COUNT_BASED, pencere 10, en az 5 çağrı, hata oranı %50, yavaş çağrı oranı %50, açık kalma 30 s, yarı açıkta 2 deneme, otomatik yarı-açığa geçiş kapalı. Yavaş eşikleri: `ai` 30 s, `payment` 10 s, `efatura` 20 s.
- "Kesinti" (hata sayılır / tekrar denenir) = `ResourceAccessException` (bağlantı, zaman aşımı) veya `RestClientResponseException` ile durum `>= 500`, `401`, `402`, `403`, `429` (= `IntegrationOutcomes.isOutageStatus`). Diğer her şey (400, 404, ayrıştırma hatası, `CallNotPermittedException`) hata SAYILMAZ ve tekrar DENENMEZ.
- Yazma çağrıları (`iyzico initializeCheckout`, `FaturaEntegratör submit`, AI üretim) ASLA tekrar denenmez.
- Açık devre yüzünden yapılmayan çağrı `IntegrationOutcomes`'a KAYDEDİLMEZ; her gerçek HTTP denemesi Faz 3'teki gibi kaydedilir.
- Mesajlar: AI yoğun → 503 `AI_BUSY` "AI şu an yoğun, lütfen birkaç saniye sonra tekrar deneyin."; ödeme → 503 `PAYMENT_PROVIDER_UNAVAILABLE` "Ödeme sistemine şu an ulaşılamıyor, lütfen birkaç dakika sonra tekrar deneyin."
- AI HTTP: `OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(60))`; FaturaEntegratör: `(5 s, 30 s)`.
- `BackoffSchedule.OUTBOX`: 2 dk, 10 dk, 1 sa, 6 sa, sonra `null` (mevcut davranış birebir).

## Review Focus

1. **Açık devrede e-fatura durum sorgusu faturayı kalıcı "başarısız" yapmamalı.** `ApplyEInvoiceCallbackUseCase`, `fetchStatus` başarısız dönerse kaydı `FAILED` işaretliyor; açık devre "hala işleniyor" (pending) dönmeli ki fatura yanlışlıkla başarısız sayılıp elle tekrar gönderilmesin (çift fatura). Task 6'da `should_returnPending_when_breakerOpen_onFetchStatus`.
2. **"AI yoğun" durumunda kota hakkı ayrılmamalı ve model çağrılmamalı.** Task 3'te `should_notReserveQuota_when_bulkheadFull`.
3. **Ödeme başlatma hata aldığında ikinci HTTP isteği gitmemeli.** Task 5'te `should_notRetryInitialize`.
4. **Açık devre `IntegrationOutcomes` sayaçlarını şişirmemeli** (yoksa Faz 3 "servis bozuk" alarmı sahte tetiklenir). Task 4 ve Task 6'da sayaç doğrulamaları.
5. **Uygulama bağlamı starter ile açılmalı ve yml değerleri gerçekten uygulanmalı** (yanlış anahtar adı sessizce varsayılana düşer). Task 1'de `ResilienceWiringTest` her instance'ın yapılandırmasını tek tek doğrular.

---

### Task 1: Kütüphane, ortak resilience paketi, yml, 503 eşlemesi

**Files:**
- Modify: `backend/pom.xml`
- Create: `backend/src/main/java/com/vetos/platform/resilience/package-info.java`, `OutageFailurePredicate.java`, `CircuitBreakerStateLogger.java`, `BackoffSchedule.java`
- Modify: `backend/src/main/resources/application.yml`
- Modify: `backend/src/main/java/com/vetos/platform/web/GlobalExceptionHandler.java` (`resolveStatus`)
- Test: `backend/src/test/java/com/vetos/platform/resilience/OutageFailurePredicateTest.java`, `BackoffScheduleTest.java`, `ResilienceWiringTest.java`
- Test: `backend/src/test/java/com/vetos/platform/web/GlobalExceptionHandlerServiceUnavailableTest.java`

**Interfaces (Produces):**
- `public class OutageFailurePredicate implements java.util.function.Predicate<Throwable>` (public no-arg ctor; `static boolean isOutage(Throwable)`)
- `public final class BackoffSchedule { public static final BackoffSchedule OUTBOX; public BackoffSchedule(Duration... steps); public Instant nextRetryAt(int attemptCountAfterThisFailure, Instant now); }`
- `CircuitBreakerStateLogger` (Spring bean, log satırları spec §4.2)
- Spring bean'leri (starter): `CircuitBreakerRegistry`, `BulkheadRegistry`, `RetryRegistry`
- `GlobalExceptionHandler`: `DomainException` alt sınıfı adı `ServiceUnavailableException` ile bitiyorsa → 503

- [ ] **Step 1: Bağımlılık**

`backend/pom.xml` `<dependencies>` içine:

```xml
        <dependency>
            <groupId>io.github.resilience4j</groupId>
            <artifactId>resilience4j-spring-boot3</artifactId>
            <version>2.4.0</version>
        </dependency>
```

Çalıştır: `./mvnw -q dependency:tree -Dincludes=org.slf4j:slf4j-api` → çıktıyı rapora yapıştır; tek `slf4j-api` sürümü (Boot'un 2.x'i) olmalı.

- [ ] **Step 2: Başarısız testler**

`OutageFailurePredicateTest.java`:

```java
package com.vetos.platform.resilience;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class OutageFailurePredicateTest {

    private final OutageFailurePredicate predicate = new OutageFailurePredicate();

    @Test
    void should_treatConnectionAndTimeoutAsOutage() {
        assertThat(predicate.test(new ResourceAccessException("timeout", new IOException("read timed out")))).isTrue();
    }

    @Test
    void should_treatOutageStatusesAsOutage() {
        assertThat(predicate.test(HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE, "x", null, null, null))).isTrue();
        assertThat(predicate.test(HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS, "x", null, null, null))).isTrue();
        assertThat(predicate.test(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "x", null, null, null))).isTrue();
        assertThat(predicate.test(HttpClientErrorException.create(HttpStatus.FORBIDDEN, "x", null, null, null))).isTrue();
    }

    @Test
    void should_notTreatClientErrorsOrOtherExceptionsAsOutage() {
        assertThat(predicate.test(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "x", null, null, null))).isFalse();
        assertThat(predicate.test(HttpClientErrorException.create(HttpStatus.NOT_FOUND, "x", null, null, null))).isFalse();
        assertThat(predicate.test(new IllegalStateException("json"))).isFalse();
        assertThat(predicate.test(CallNotPermittedException.createCallNotPermittedException(CircuitBreaker.ofDefaults("x")))).isFalse();
    }
}
```

`BackoffScheduleTest.java`:

```java
package com.vetos.platform.resilience;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class BackoffScheduleTest {

    private static final Instant NOW = Instant.parse("2026-10-16T10:00:00Z");

    @Test
    void should_followOutboxSchedule_thenStop() {
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(1, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(2, NOW)).isEqualTo(NOW.plus(Duration.ofMinutes(10)));
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(3, NOW)).isEqualTo(NOW.plus(Duration.ofHours(1)));
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(4, NOW)).isEqualTo(NOW.plus(Duration.ofHours(6)));
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(5, NOW)).isNull();
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(9, NOW)).isNull();
    }

    @Test
    void should_returnNull_forNonPositiveAttempt() {
        assertThat(BackoffSchedule.OUTBOX.nextRetryAt(0, NOW)).isNull();
    }
}
```

`GlobalExceptionHandlerServiceUnavailableTest.java`:

```java
package com.vetos.platform.web;

import com.vetos.platform.exception.DomainException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerServiceUnavailableTest {

    static class SampleServiceUnavailableException extends DomainException {
        SampleServiceUnavailableException() {
            super("SAMPLE_UNAVAILABLE", "Servis şu an kullanılamıyor");
        }
    }

    @Test
    void should_map_ServiceUnavailableSuffix_to503() {
        var response = new GlobalExceptionHandler().handleDomainException(
            new SampleServiceUnavailableException(), new MockHttpServletRequest("POST", "/x"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody().errorCode()).isEqualTo("SAMPLE_UNAVAILABLE");
    }
}
```

`ResilienceWiringTest.java` (canlı Postgres):

```java
package com.vetos.platform.resilience;

import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Canli docker-compose Postgres gerekir. yml anahtarlari yanlis yazilirsa sessizce varsayilana duser -- burada tek tek dogrulanir. */
@SpringBootTest
class ResilienceWiringTest {

    @Autowired private CircuitBreakerRegistry circuitBreakers;
    @Autowired private BulkheadRegistry bulkheads;
    @Autowired private RetryRegistry retries;
    @Autowired private MeterRegistry meters;

    @Test
    void should_configureCircuitBreakers_fromYaml() {
        Map<String, Duration> slow = Map.of("ai", Duration.ofSeconds(30), "payment", Duration.ofSeconds(10), "efatura", Duration.ofSeconds(20));
        slow.forEach((name, threshold) -> {
            CircuitBreakerConfig c = circuitBreakers.circuitBreaker(name).getCircuitBreakerConfig();
            assertThat(c.getSlidingWindowType()).as(name).isEqualTo(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED);
            assertThat(c.getSlidingWindowSize()).as(name).isEqualTo(10);
            assertThat(c.getMinimumNumberOfCalls()).as(name).isEqualTo(5);
            assertThat(c.getFailureRateThreshold()).as(name).isEqualTo(50f);
            assertThat(c.getSlowCallRateThreshold()).as(name).isEqualTo(50f);
            assertThat(c.getSlowCallDurationThreshold()).as(name).isEqualTo(threshold);
            assertThat(c.getPermittedNumberOfCallsInHalfOpenState()).as(name).isEqualTo(2);
            assertThat(c.isAutomaticTransitionFromOpenToHalfOpenEnabled()).as(name).isFalse();
            assertThat(c.getRecordExceptionPredicate().test(new ResourceAccessException("x"))).as(name).isTrue();
            assertThat(c.getRecordExceptionPredicate().test(new IllegalStateException("x"))).as(name).isFalse();
        });
        assertThat(circuitBreakers.getAllCircuitBreakers()).extracting(cb -> cb.getName()).contains("ai", "payment", "efatura");
    }

    @Test
    void should_configureAiBulkhead() {
        var c = bulkheads.bulkhead("ai").getBulkheadConfig();
        assertThat(c.getMaxConcurrentCalls()).isEqualTo(4);
        assertThat(c.getMaxWaitDuration()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void should_configureReadRetries() {
        for (String name : new String[] {"payment-read", "efatura-read"}) {
            var c = retries.retry(name).getRetryConfig();
            assertThat(c.getMaxAttempts()).as(name).isEqualTo(3);
            assertThat(c.getExceptionPredicate().test(new ResourceAccessException("x"))).as(name).isTrue();
            assertThat(c.getExceptionPredicate().test(new IllegalStateException("x"))).as(name).isFalse();
        }
    }

    @Test
    void should_exposeMetrics() {
        assertThat(meters.find("resilience4j.circuitbreaker.state").tag("name", "ai").meters()).isNotEmpty();
        assertThat(meters.find("resilience4j.bulkhead.available.concurrent.calls").tag("name", "ai").meters()).isNotEmpty();
    }
}
```

(Metrik adları Resilience4j 2.4.0 Micrometer modülündeki gerçek adlardan farklıysa — `meters.getMeters()` ile listele — gerçek adı kullan ve Deviations'a yaz. `getRecordExceptionPredicate` / `getExceptionPredicate` getter adları 2.4.0 API'sinde farklıysa gerçek adlarla uyarla.)

- [ ] **Step 3: RED** — `./mvnw -q test -Dtest='OutageFailurePredicateTest,BackoffScheduleTest,GlobalExceptionHandlerServiceUnavailableTest'` → derleme hatası / başarısız.

- [ ] **Step 4: Uygula**

`package-info.java`:

```java
@org.springframework.modulith.NamedInterface("resilience")
package com.vetos.platform.resilience;
```

`OutageFailurePredicate.java`:

```java
package com.vetos.platform.resilience;

import com.vetos.platform.observability.IntegrationOutcomes;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

import java.util.function.Predicate;

/**
 * Devre kesici "hata" ve okuma retry'i "tekrar dene" karari (spec 2026-10-16 §4.2): yalniz saglayici
 * kesintisi -- baglanti/zaman asimi veya IntegrationOutcomes.isOutageStatus durumlari. Bizim taraftaki
 * 4xx, ayristirma hatasi ve acik devre (CallNotPermittedException) sayilmaz. yml'de sinif adiyla
 * baglanir (record-failure-predicate / retry-exception-predicate) -- public bos kurucu sart.
 */
public class OutageFailurePredicate implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable throwable) {
        return isOutage(throwable);
    }

    public static boolean isOutage(Throwable throwable) {
        if (throwable instanceof RestClientResponseException e) {
            return IntegrationOutcomes.isOutageStatus(e.getStatusCode().value());
        }
        return throwable instanceof ResourceAccessException;
    }
}
```

`BackoffSchedule.java`:

```java
package com.vetos.platform.resilience;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Outbox (e-fatura, bildirim) otomatik tekrar deneme takvimi -- iki executor'daki kopya
 * RETRY_BACKOFF tablolarinin tek kaynagi. attemptCountAfterThisFailure 1..n -> sonraki deneme
 * zamani; takvim bitince null (yalniz elle "Tekrar Dene").
 */
public final class BackoffSchedule {

    public static final BackoffSchedule OUTBOX = new BackoffSchedule(
        Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6)
    );

    private final List<Duration> steps;

    public BackoffSchedule(Duration... steps) {
        this.steps = List.of(steps);
    }

    public Instant nextRetryAt(int attemptCountAfterThisFailure, Instant now) {
        int index = attemptCountAfterThisFailure - 1;
        if (index < 0 || index >= steps.size()) {
            return null;
        }
        return now.plus(steps.get(index));
    }
}
```

`CircuitBreakerStateLogger.java`:

```java
package com.vetos.platform.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Tum devre kesicilerin durum degisimini loglar (spec §4.2): ACIK -> WARN, KAPALI -> INFO. */
@Component
class CircuitBreakerStateLogger {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerStateLogger.class);

    CircuitBreakerStateLogger(CircuitBreakerRegistry registry) {
        registry.getAllCircuitBreakers().forEach(this::attach);
        registry.getEventPublisher().onEntryAdded(event -> attach(event.getAddedEntry()));
    }

    private void attach(CircuitBreaker circuitBreaker) {
        circuitBreaker.getEventPublisher().onStateTransition(event -> {
            CircuitBreaker.State to = event.getStateTransition().getToState();
            if (to == CircuitBreaker.State.OPEN || to == CircuitBreaker.State.FORCED_OPEN) {
                log.warn("Devre kesici ACIK: {} (sonraki deneme {} sn sonra)", circuitBreaker.getName(),
                    circuitBreaker.getCircuitBreakerConfig().getWaitIntervalFunctionInOpenState().apply(1) / 1000);
            } else if (to == CircuitBreaker.State.CLOSED) {
                log.info("Devre kesici kapandi: {}", circuitBreaker.getName());
            }
        });
    }
}
```

(`getWaitIntervalFunctionInOpenState()` 2.4.0'da yoksa bekleme süresini yazmadan `"Devre kesici ACIK: {}"` logla; Deviations'a yaz.)

`application.yml` (en sona, kök seviyede):

```yaml
# Faz 5 (spec 2026-10-16): dis servis korumasi. Kod programatik sarar (anotasyon yok).
resilience4j:
  circuitbreaker:
    configs:
      default:
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        failure-rate-threshold: 50
        slow-call-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 2
        automatic-transition-from-open-to-half-open-enabled: false
        record-failure-predicate: com.vetos.platform.resilience.OutageFailurePredicate
    instances:
      ai:
        base-config: default
        slow-call-duration-threshold: 30s
      payment:
        base-config: default
        slow-call-duration-threshold: 10s
      efatura:
        base-config: default
        slow-call-duration-threshold: 20s
  bulkhead:
    instances:
      ai:
        max-concurrent-calls: 4
        max-wait-duration: 10s
  retry:
    configs:
      read:
        max-attempts: 3
        wait-duration: 500ms
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exception-predicate: com.vetos.platform.resilience.OutageFailurePredicate
    instances:
      payment-read:
        base-config: read
      efatura-read:
        base-config: read
```

`GlobalExceptionHandler.resolveStatus`'a, `ForbiddenException` kontrolünden önce:

```java
        if (name.endsWith("ServiceUnavailableException")) {
            return HttpStatus.SERVICE_UNAVAILABLE;
        }
```

- [ ] **Step 5: GREEN** — `./mvnw -q test -Dtest='OutageFailurePredicateTest,BackoffScheduleTest,GlobalExceptionHandlerServiceUnavailableTest,ResilienceWiringTest,ApplicationModulesTest,ObservabilityWiringTest'` → PASS. Uygulama context'i AOP olmadan açılmıyorsa `spring-boot-starter-aop` ekle (Deviations).

---

### Task 2: Outbox tekrar deneme takviminin tekilleştirilmesi

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutor.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/application/NotificationSendExecutor.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/package-info.java`, `.../notification/package-info.java` (`"platform::resilience"`)
- Test: mevcut `EInvoiceSubmissionExecutorTest`, `NotificationSendExecutorTest` (varsa) aynen geçmeli

**Interfaces:** Consumes `BackoffSchedule.OUTBOX.nextRetryAt(int, Instant)` (Task 1).

- [ ] **Step 1: Mevcut testleri çalıştır (referans)** — `./mvnw -q test -Dtest='EInvoiceSubmissionExecutorTest,NotificationSendExecutorTest'` (olmayan sınıfı listeden çıkar); sonuçları rapora yaz. `computeNextRetryAt`'i doğrudan test eden test yoksa her executor test sınıfına ekle:

```java
    @Test
    void should_keepOutboxBackoff() {
        Instant before = Instant.now();
        Instant first = executor.computeNextRetryAt(1);
        assertThat(first).isBetween(before.plus(Duration.ofMinutes(2)), Instant.now().plus(Duration.ofMinutes(2)));
        assertThat(executor.computeNextRetryAt(4)).isAfter(Instant.now().plus(Duration.ofHours(5)));
        assertThat(executor.computeNextRetryAt(5)).isNull();
    }
```

(`executor` test sınıfındaki mevcut örnek adı; yoksa mevcut kurulumla oluştur.)

- [ ] **Step 2: Uygula** — her iki executor'da `RETRY_BACKOFF` dizisini sil; `computeNextRetryAt` gövdesi:

```java
    Instant computeNextRetryAt(int attemptCountAfterThisFailure) {
        return BackoffSchedule.OUTBOX.nextRetryAt(attemptCountAfterThisFailure, Instant.now());
    }
```

`NotificationSendExecutor`'daki "attemptCount 1..4 -> ..." açıklama yorumunu koru (`BackoffSchedule.OUTBOX`'a işaret edecek şekilde). İki package-info listesine `"platform::resilience"` ekle.

- [ ] **Step 3: GREEN** — Step 1 komutu + `ApplicationModulesTest` + `./mvnw -q test -Dtest='*EInvoice*,*Notification*'` → PASS.

---

### Task 3: AI eşzamanlılık sınırı (`AiCallGate`)

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/ai/application/AiCallGate.java`
- Create: `backend/src/main/java/com/vetos/modules/ai/domain/AiBusyServiceUnavailableException.java` (ai modülünün mevcut domain exception paketinde — `find backend/src/main/java/com/vetos/modules/ai -name "*Exception.java"` ile yerini bul)
- Modify: `GenerateSoapDraftUseCase.java`, `GenerateDiagnosisSuggestionUseCase.java`, `GenerateTreatmentRecommendationUseCase.java`
- Test: `backend/src/test/java/com/vetos/modules/ai/application/AiCallGateTest.java` + üç use case testinin güncellenmesi

**Interfaces:**
- Consumes: `BulkheadRegistry` bean (Task 1, instance `ai`), Faz 4 `UsageQuotaPort`.
- Produces: `public class AiCallGate { public AiCallGate(BulkheadRegistry registry); public <T> T run(Supplier<T> work); }` (yer yoksa `AiBusyServiceUnavailableException`); `AiBusyServiceUnavailableException()` — errorCode `AI_BUSY`, mesaj "AI şu an yoğun, lütfen birkaç saniye sonra tekrar deneyin."

Sıra (spec §4.1): doğrulama → `aiCallGate.run(() -> { reserve → generate → (hata/modelConnected=false ise release) → return draft })` → (Diagnosis/Treatment) AI iş kaydı. Yani mevcut "reserve…release" bloğu `run` içine taşınır; doğrulama ve `recordAiJobUseCase` dışarıda kalır.

- [ ] **Step 1: Başarısız testler**

`AiCallGateTest.java`:

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiBusyServiceUnavailableException;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiCallGateTest {

    private AiCallGate gate(int max, Duration wait) {
        return new AiCallGate(BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(max).maxWaitDuration(wait).build()));
    }

    @Test
    void should_runWork_andReturnResult() {
        assertThat(gate(4, Duration.ofMillis(100)).run(() -> "ok")).isEqualTo("ok");
    }

    @Test
    void should_rejectWithAiBusy_when_fullBeyondWait() throws Exception {
        AiCallGate gate = gate(1, Duration.ofMillis(100));
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> holder = pool.submit(() -> gate.run(() -> {
                inside.countDown();
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "first";
            }));
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> gate.run(() -> "second"))
                .isInstanceOf(AiBusyServiceUnavailableException.class)
                .hasMessage("AI şu an yoğun, lütfen birkaç saniye sonra tekrar deneyin.");

            release.countDown();
            assertThat(holder.get(5, TimeUnit.SECONDS)).isEqualTo("first");
            assertThat(gate.run(() -> "third")).isEqualTo("third");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void should_admitWaitingCall_when_slotFreesWithinWait() throws Exception {
        AiCallGate gate = gate(1, Duration.ofSeconds(2));
        CountDownLatch inside = new CountDownLatch(1);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            pool.submit(() -> gate.run(() -> {
                inside.countDown();
                try {
                    Thread.sleep(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "first";
            }));
            assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(gate.run(() -> "second")).isEqualTo("second");
        } finally {
            pool.shutdownNow();
        }
    }
}
```

Üç use case testinde: constructor'a `AiCallGate` ekle (`new AiCallGate(BulkheadRegistry.of(BulkheadConfig.custom().maxConcurrentCalls(4).build()))` — mevcut testler değişmeden geçmeli) ve her birine (Review Focus #2):

```java
    @Test
    void should_notReserveQuota_when_bulkheadFull() {
        AiCallGate fullGate = mock(AiCallGate.class);
        when(fullGate.run(any())).thenThrow(new AiBusyServiceUnavailableException());
        // use case'i fullGate ile kur (diger bagimliliklar mevcut testteki gibi), gecerli girdiyle cagir
        assertThatThrownBy(() -> /* useCase.execute(...) */ null).isInstanceOf(AiBusyServiceUnavailableException.class);
        verifyNoInteractions(quota);
        verifyNoInteractions(/* model portu */);
    }
```

(Yorumlu kısımları her test dosyasının gerçek değişken adlarıyla doldur: SOAP için `useCase.execute(TENANT, "metin")` ve `port`; Diagnosis/Treatment için mevcut başarılı senaryonun komutu ve port mock'u. `AiCallGate` sınıfı `final` olmamalı ki mock'lanabilsin.)

- [ ] **Step 2: RED** — `./mvnw -q test -Dtest='AiCallGateTest,GenerateSoapDraftUseCaseTest,GenerateDiagnosisSuggestionUseCaseTest,GenerateTreatmentRecommendationUseCaseTest'` → derleme hatası.

- [ ] **Step 3: Uygula**

`AiBusyServiceUnavailableException.java`:

```java
package com.vetos.modules.ai.domain;   // (gercek exception paketine uyarla)

import com.vetos.platform.exception.DomainException;

/** Sinif adi "ServiceUnavailableException" ile bittigi icin GlobalExceptionHandler 503 doner. */
public class AiBusyServiceUnavailableException extends DomainException {

    public AiBusyServiceUnavailableException() {
        super("AI_BUSY", "AI şu an yoğun, lütfen birkaç saniye sonra tekrar deneyin.");
    }
}
```

`AiCallGate.java`:

```java
package com.vetos.modules.ai.application;

import com.vetos.modules.ai.domain.AiBusyServiceUnavailableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * AI cagrilari icin esZamanlilik siniri (spec 2026-10-16 §4.1): en fazla 4 cagri, 5.'si en fazla
 * 10 sn bekler, yer acilmazsa 503 AI_BUSY. Kota ayirma bu kapinin ICINDE yapilir -- yer yoksa hak dusmez.
 */
@Component
public class AiCallGate {

    private final Bulkhead bulkhead;

    public AiCallGate(BulkheadRegistry registry) {
        this.bulkhead = registry.bulkhead("ai");
    }

    public <T> T run(Supplier<T> work) {
        try {
            return bulkhead.executeSupplier(work);
        } catch (BulkheadFullException e) {
            throw new AiBusyServiceUnavailableException();
        }
    }
}
```

Use case'ler: constructor'a `AiCallGate aiCallGate` (son parametre / son alan) ekle; mevcut `reserve … release` bloğunu `aiCallGate.run(() -> { ... return draft; })` içine al. Örnek (SOAP):

```java
    public SoapDraft execute(UUID tenantId, String transcriptText) {
        return aiCallGate.run(() -> {
            usageQuotaPort.reserve(tenantId, UsageKind.AI, 1);
            SoapDraft draft;
            try {
                draft = soapGenerationPort.generate(new AudioTranscript(transcriptText));
            } catch (RuntimeException e) {
                usageQuotaPort.release(tenantId, UsageKind.AI, 1);
                throw e;
            }
            if (!draft.modelConnected()) {
                usageQuotaPort.release(tenantId, UsageKind.AI, 1);
            }
            return draft;
        });
    }
```

Diagnosis/Treatment'ta doğrulama ve `input` oluşturma `run`'dan önce, `recordAiJobUseCase.execute(...)` `run`'dan sonra kalır.

- [ ] **Step 4: GREEN** — Step 2 komutu + `ApplicationModulesTest,TenantRateLimitWiringTest` → PASS.

---

### Task 4: AI adaptörleri — devre kesici ve ortak HTTP istemcisi

**Files:**
- Modify (`backend/src/main/java/com/vetos/modules/ai/infrastructure/adapter/`): `ClaudeSoapGenerationAdapter.java`, `ClaudeDiagnosisSuggestionAdapter.java`, `ClaudeTreatmentRecommendationAdapter.java`, `OllamaSoapGenerationAdapter.java`, `OllamaDiagnosisSuggestionAdapter.java`, `OllamaTreatmentRecommendationAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/ai/package-info.java` (`"platform::http"` yoksa ekle)
- Test: mevcut 4 adaptör testi (constructor güncellemesi) + `backend/src/test/java/com/vetos/modules/ai/infrastructure/adapter/AiCircuitBreakerAdapterTest.java`

**Interfaces:** Consumes `CircuitBreakerRegistry` (Task 1, instance `ai`), `OutboundHttp.restClient(Duration, Duration)`, `IntegrationOutcomes.recordAiCall(String task, String provider, Callable<T>)`.

Her adaptörde:
1. Kendi `SimpleClientHttpRequestFactory` bloğunu sil → `this.restClient = OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(60));`
2. Constructor'lara (hem `@Autowired` hem test kurucusu) son parametre `CircuitBreakerRegistry circuitBreakers` ekle; alan `this.circuitBreaker = circuitBreakers.circuitBreaker("ai");`
3. HTTP çağrısını sar — devre kesici `recordAiCall`'ın DIŞINDA (açık devre `IntegrationOutcomes`'a kayıt düşmesin):

```java
        try {
            return circuitBreaker.executeCallable(() -> outcomes.recordAiCall("soap", "claude", () -> generateViaClaude(text)));
        } catch (Exception e) {
            log.warn("Claude SOAP uretimi basarisiz, mock'a duseluyor: {}", e.getMessage());
            return fallback(text);
        }
```

(Görev adı ve sağlayıcı her adaptörde mevcut `recordAiCall` argümanlarıyla aynı kalır. Mevcut `catch` bloğu `CallNotPermittedException`'ı da yakalar → boş taslak.)

- [ ] **Step 1: Başarısız testler**

Mevcut 4 adaptör testindeki kurucu çağrılarına `CircuitBreakerRegistry.ofDefaults()` ekle (davranış değişmemeli). `AiCircuitBreakerAdapterTest.java` (sahte sunucu deseni mevcut `ClaudeSoapGenerationAdapterTest` ile aynı):

```java
package com.vetos.modules.ai.infrastructure.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vetos.modules.ai.domain.AudioTranscript;
import com.vetos.modules.ai.domain.SoapDraft;
import com.vetos.platform.observability.IntegrationOutcomes;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import com.vetos.platform.resilience.OutageFailurePredicate;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class AiCircuitBreakerAdapterTest {

    private static final String OK_BODY = """
        {"content":[{"type":"tool_use","input":{"subjective":"s","objective":"o","assessment":"a","plan":"p"}}]}
        """;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile int status = 500;
    private SimpleMeterRegistry meters;
    private IntegrationOutcomes outcomes;
    private CircuitBreakerRegistry registry;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/messages", ex -> {
            requests.incrementAndGet();
            byte[] body = (status == 200 ? OK_BODY : "{}").getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, body.length);
            ex.getResponseBody().write(body);
            ex.close();
        });
        server.start();
        meters = new SimpleMeterRegistry();
        outcomes = new IntegrationOutcomes(meters, Clock.systemUTC());
        registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.custom()
            .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50)
            .waitDurationInOpenState(Duration.ofMillis(200)).permittedNumberOfCallsInHalfOpenState(2)
            .recordException(new OutageFailurePredicate())
            .build());
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private ClaudeSoapGenerationAdapter adapter() {
        return new ClaudeSoapGenerationAdapter("k", "m", new ObjectMapper(),
            "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/messages", outcomes, registry);
    }

    private double failures() {
        var c = meters.find("vetly.integration.calls").tag("integration", "ai").tag("outcome", "failure").counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void should_openAfterOutages_and_skipHttpWhileOpen() {
        var adapter = adapter();
        for (int i = 0; i < 4; i++) {
            assertThat(adapter.generate(new AudioTranscript("metin")).modelConnected()).isFalse();
        }
        assertThat(registry.circuitBreaker("ai").getState()).isEqualTo(CircuitBreaker.State.OPEN);
        int before = requests.get();
        double failuresBefore = failures();

        SoapDraft draft = adapter.generate(new AudioTranscript("metin"));

        assertThat(draft.modelConnected()).isFalse();
        assertThat(requests.get()).isEqualTo(before);          // HTTP gitmedi
        assertThat(failures()).isEqualTo(failuresBefore);      // acik devre IntegrationOutcomes'a yazilmadi
    }

    @Test
    void should_closeAfterHalfOpenSuccesses() throws Exception {
        var adapter = adapter();
        for (int i = 0; i < 4; i++) {
            adapter.generate(new AudioTranscript("metin"));
        }
        Thread.sleep(250);
        status = 200;
        assertThat(adapter.generate(new AudioTranscript("metin")).modelConnected()).isTrue();
        assertThat(adapter.generate(new AudioTranscript("metin")).modelConnected()).isTrue();
        assertThat(registry.circuitBreaker("ai").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }

    @Test
    void should_notOpen_onClientErrors() {
        status = 400;
        var adapter = adapter();
        for (int i = 0; i < 6; i++) {
            adapter.generate(new AudioTranscript("metin"));
        }
        assertThat(registry.circuitBreaker("ai").getState()).isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
```

(Yarı-açığa geçiş otomatik değil: bekleme süresi dolunca bir sonraki çağrı yarı-açığa geçirir — test bunu kullanır. Sahte sunucunun döndürdüğü 200 gövdesi mevcut `ClaudeSoapGenerationAdapterTest`'teki başarılı gövdeyle aynı biçimde olmalı; farklıysa oradakini kopyala.)

- [ ] **Step 2: RED** — `./mvnw -q test -Dtest='AiCircuitBreakerAdapterTest'` → derleme hatası.
- [ ] **Step 3: Uygula** — 6 adaptör (yukarıdaki 3 madde); `ai/package-info.java`'ya `"platform::http"` (yoksa).
- [ ] **Step 4: GREEN** — `./mvnw -q test -Dtest='AiCircuitBreakerAdapterTest,ClaudeSoapGenerationAdapterTest,ClaudeDiagnosisSuggestionAdapterTest,ClaudeTreatmentRecommendationAdapterTest,OllamaTreatmentRecommendationAdapterTest,ApplicationModulesTest,ObservabilityWiringTest'` → PASS.

---

### Task 5: iyzico — ödeme başlatmada devre kesici, sonuç sorgusunda tekrar deneme

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapter.java`
- Create: `PaymentProviderServiceUnavailableException.java` (platformadmin modülünün domain exception paketinde — `find ... -name "PaymentGatewayException.java"` ile yanına)
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapterTest.java` (genişlet)

**Interfaces:** Consumes `CircuitBreakerRegistry` (`payment`), `RetryRegistry` (`payment-read`) (Task 1). Produces `PaymentProviderServiceUnavailableException()` — errorCode `PAYMENT_PROVIDER_UNAVAILABLE`, mesaj "Ödeme sistemine şu an ulaşılamıyor, lütfen birkaç dakika sonra tekrar deneyin." (503).

Constructor'a son iki parametre: `CircuitBreakerRegistry circuitBreakers, RetryRegistry retries` → alanlar `paymentBreaker = circuitBreakers.circuitBreaker("payment")`, `readRetry = retries.retry("payment-read")`.

- `initializeCheckout`: `response = postSigned(INITIALIZE_PATH, requestBody);` → `response = paymentBreaker.executeSupplier(() -> postSigned(INITIALIZE_PATH, requestBody));` ve mevcut `catch`'lerden ÖNCE:

```java
        } catch (CallNotPermittedException e) {
            log.warn("iyzico devre kesici acik, odeme baslatilmadi: conversationId={}", conversationId);
            throw new PaymentProviderServiceUnavailableException();
```

(Açık devrede `outcomes.record` YOK. Tekrar deneme YOK.)

- `retrieveCheckoutResult`: gerçek isteği ve sonucun kaydını tek denemelik özel metoda taşı, dışını retry ile sar:

```java
    /** Tek gercek HTTP denemesi + IntegrationOutcomes kaydi (her deneme bir kayit). */
    private Map<String, Object> retrieveOnce(String requestBody) {
        try {
            Map<String, Object> response = postSigned(RETRIEVE_PATH, requestBody);
            outcomes.record(IntegrationOutcomes.PAYMENT, response != null);
            return response;
        } catch (RestClientResponseException e) {
            outcomes.record(IntegrationOutcomes.PAYMENT, !IntegrationOutcomes.isOutageStatus(e.getStatusCode().value()));
            throw e;
        } catch (RestClientException e) {
            outcomes.record(IntegrationOutcomes.PAYMENT, false);
            throw e;
        }
    }
```

ve `retrieveCheckoutResult` içinde `Map<String, Object> response = readRetry.executeSupplier(() -> retrieveOnce(requestBody));` — dış `catch (RestClientResponseException e)` artık `outcomes.record` YAPMAZ (içeride yapıldı), yalnız log + `new CheckoutResult(false, null, null)`; dış `catch (RestClientException e)` yalnız `throw e`.

- [ ] **Step 1: Başarısız testler** (mevcut dosyanın `HttpServer` sahte sunucu kurulumunu kullan; mevcut kurucu çağrılarına `CircuitBreakerRegistry.ofDefaults(), RetryRegistry.ofDefaults()` ekle). Testte retry için kısa bekleme: `RetryRegistry.of(RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(10)).retryOnException(new OutageFailurePredicate()).build())`. Yeni testler:

```java
    @Test
    void should_failFastWith503_when_breakerOpen() {
        CircuitBreakerRegistry breakers = CircuitBreakerRegistry.ofDefaults();
        breakers.circuitBreaker("payment").transitionToOpenState();
        var adapter = /* yapilandirilmis adapter, sahte sunucu URL'si, breakers, retries */;
        int before = requestCount.get();

        assertThatThrownBy(() -> adapter.initializeCheckout("conv-1", new BigDecimal("100"), "Ad Soyad", "a@b.com"))
            .isInstanceOf(PaymentProviderServiceUnavailableException.class)
            .hasMessage("Ödeme sistemine şu an ulaşılamıyor, lütfen birkaç dakika sonra tekrar deneyin.");
        assertThat(requestCount.get()).isEqualTo(before);
    }

    @Test
    void should_notRetryInitialize() {
        // sahte sunucu /payment/iyzipos/checkoutform/initialize/auth/ecom -> 503
        assertThatThrownBy(() -> adapter.initializeCheckout("conv-1", new BigDecimal("100"), "Ad Soyad", "a@b.com"))
            .isInstanceOf(PaymentGatewayException.class);
        assertThat(initializeRequests.get()).isEqualTo(1);
    }

    @Test
    void should_retryRetrieve_onOutage_thenSucceed() {
        // sahte sunucu retrieve yolu: ilk iki istek 503, ucuncu 200 {"status":"success","paymentStatus":"SUCCESS","conversationId":"conv-1","paymentId":"p1"}
        CheckoutResult result = adapter.retrieveCheckoutResult("tok");
        assertThat(result.success()).isTrue();
        assertThat(retrieveRequests.get()).isEqualTo(3);
    }

    @Test
    void should_notRetryRetrieve_onClientError() {
        // retrieve yolu -> 400
        assertThat(adapter.retrieveCheckoutResult("tok").success()).isFalse();
        assertThat(retrieveRequests.get()).isEqualTo(1);
    }
```

(Yol sabitleri `INITIALIZE_PATH` / `RETRIEVE_PATH`'in gerçek değerlerini adaptörden oku; yorumlu yerleri mevcut test dosyasının sahte sunucu yardımcılarıyla doldur. Sahte sunucuda yol başına sayaç tut.)

- [ ] **Step 2: RED** — `./mvnw -q test -Dtest=IyzicoPaymentGatewayAdapterTest` → derleme hatası/başarısız.
- [ ] **Step 3: Uygula** — yukarıdaki değişiklikler + exception sınıfı.
- [ ] **Step 4: GREEN** — `./mvnw -q test -Dtest='IyzicoPaymentGatewayAdapterTest,*Checkout*,*Payment*,ApplicationModulesTest'` → PASS.

---

### Task 6: FaturaEntegratör — devre kesici, durum sorgusunda tekrar deneme, ortak HTTP istemcisi

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/adapter/FaturaEntegratorEInvoiceGatewayAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/package-info.java` (`"platform::http"` yoksa)
- Test: `backend/src/test/java/com/vetos/modules/integration/efatura/infrastructure/adapter/FaturaEntegratorEInvoiceGatewayAdapterTest.java` (genişlet)

**Interfaces:** Consumes `CircuitBreakerRegistry` (`efatura`), `RetryRegistry` (`efatura-read`), `OutboundHttp`.

Constructor'lara son iki parametre `CircuitBreakerRegistry circuitBreakers, RetryRegistry retries`; HTTP istemcisi `OutboundHttp.restClient(Duration.ofSeconds(5), Duration.ofSeconds(30))`.

- `submit`: `String raw = restClient.post()...body(String.class);` → `String raw = efaturaBreaker.executeSupplier(() -> restClient.post()...body(String.class));` ve `catch (RestClientResponseException e)`'den ÖNCE:

```java
        } catch (CallNotPermittedException e) {
            // Acik devre: gercek deneme yok -> IntegrationOutcomes'a kayit YOK; gecici hata sonucu,
            // kuyruk mevcut BackoffSchedule.OUTBOX takvimiyle yeniden planlar.
            log.warn("faturaentegrator devre kesici acik, gonderim ertelendi");
            return EInvoiceSubmissionOutcome.failure("faturaentegrator gecici olarak devre disi (devre kesici acik)");
```

Tekrar deneme YOK.

- `fetchStatus`: tek denemelik özel metot + retry(breaker(...)) sırası (her deneme devre kesiciye ayrı sayılır, açık devre tekrar denenmez):

```java
    /** Tek gercek GET denemesi; kesinti siniflari IntegrationOutcomes'a burada yazilir. */
    private String fetchOnce(String providerReference) {
        try {
            return restClient.get()
                .uri(apiBaseUrl + "/invoices/" + providerReference)
                .header("Authorization", "Bearer " + apiKey)
                .header("Accept", MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(String.class);
        } catch (RestClientResponseException e) {
            outcomes.record(IntegrationOutcomes.EFATURA, !IntegrationOutcomes.isOutageStatus(e.getStatusCode().value()));
            throw e;
        } catch (RestClientException e) {
            outcomes.record(IntegrationOutcomes.EFATURA, false);
            throw e;
        }
    }
```

`fetchStatus` içinde: `String raw = readRetry.executeSupplier(() -> efaturaBreaker.executeSupplier(() -> fetchOnce(providerReference)));` — başarılı yanıtın `outcomes.record(EFATURA, data var mı)` kaydı mevcut yerinde kalır. Dış `catch (RestClientResponseException e)` ve `catch (RestClientException e)` artık `outcomes.record` YAPMAZ (içeride, deneme başına yapıldı) — yalnız log + mevcut `failure(...)` sonucu. **Kural:** HTTP başarılı ama gövde ayrıştırılamazsa (`com.fasterxml.jackson.core.JsonProcessingException`) mevcut davranış korunur: ayrı bir `catch (JsonProcessingException e)` içinde `outcomes.record(IntegrationOutcomes.EFATURA, false)` + mevcut failure sonucu. Ve en başa:

```java
        } catch (CallNotPermittedException e) {
            // Acik devre: faturayi "basarisiz" isaretlemek (ApplyEInvoiceCallbackUseCase markFailed) yanlis
            // olur -- elle tekrar gonderim cift faturaya yol acabilir. "Hala isleniyor" doner, kayit degismez.
            log.warn("faturaentegrator devre kesici acik, durum sorgusu yapilmadi: providerReference={}", providerReference);
            return EInvoiceSubmissionOutcome.pending(providerReference, "Saglayiciya su an ulasilamiyor (devre kesici acik), durum daha sonra sorgulanacak");
```

- [ ] **Step 1: Başarısız testler** (mevcut sahte sunucu kurulumu; kurucu çağrılarına registry'ler; retry testte 10 ms bekleme):

```java
    @Test
    void should_skipHttp_and_returnTransientFailure_when_breakerOpenOnSubmit() {
        breakers.circuitBreaker("efatura").transitionToOpenState();
        int before = requests.get();
        double failuresBefore = failureCount("efatura");

        EInvoiceSubmissionOutcome outcome = adapter.submit(sampleRequest());

        assertThat(outcome.success()).isFalse();
        assertThat(outcome.finalResult()).isFalse();
        assertThat(requests.get()).isEqualTo(before);
        assertThat(failureCount("efatura")).isEqualTo(failuresBefore);
    }

    @Test
    void should_returnPending_when_breakerOpen_onFetchStatus() {
        breakers.circuitBreaker("efatura").transitionToOpenState();
        EInvoiceSubmissionOutcome outcome = adapter.fetchStatus("ref-1");
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalResult()).isFalse();
        assertThat(outcome.gibReference()).isEqualTo("ref-1");
    }

    @Test
    void should_retryFetchStatus_onOutage() {
        // GET /invoices/ref-1: ilk iki istek 503, ucuncu 200 {"data":{"ettn":"ETTN-1"}}
        EInvoiceSubmissionOutcome outcome = adapter.fetchStatus("ref-1");
        assertThat(outcome.success()).isTrue();
        assertThat(outcome.finalResult()).isTrue();
        assertThat(getRequests.get()).isEqualTo(3);
    }

    @Test
    void should_notRetrySubmit() {
        // POST /invoices -> 503
        adapter.submit(sampleRequest());
        assertThat(postRequests.get()).isEqualTo(1);
    }
```

(`sampleRequest()`, sayaçlar ve `failureCount` için mevcut test dosyasındaki yardımcıları kullan/ekle.)

- [ ] **Step 2: RED** — `./mvnw -q test -Dtest=FaturaEntegratorEInvoiceGatewayAdapterTest`
- [ ] **Step 3: Uygula**
- [ ] **Step 4: GREEN** — `./mvnw -q test -Dtest='FaturaEntegratorEInvoiceGatewayAdapterTest,*EInvoice*,ApplicationModulesTest'` → PASS.

---

### Task 7: Doğrulama, dokümantasyon, yol haritası

**Files:**
- Modify: `docs/deployment.md`, `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md`

- [ ] **Step 1: Ön yüz mesaj kontrolü** — AI (`frontend/src/pages/encounter/EncounterPage.tsx` üç AI çağrısının catch'leri) ve ödeme başlatma (`SubscriptionPanel.tsx` `initiateCheckout` ve kayıt ödemesi akışı — `grep -rn "initiateCheckout\|checkout" frontend/src --include=*.tsx`) hata yollarının `ApiError.message`'ı gösterdiğini kod okumasıyla doğrula; göstermeyen yer varsa (ör. sabit bir metin basıyorsa) en küçük düzeltmeyi yap (`errorMessageOf(err)` deseniyle) ve `npm run build` çalıştır.

- [ ] **Step 2: `docs/deployment.md`** — "İşlem geçmişi / denetim kaydı (Faz 3b)" bölümünden sonra (Türkçe karakterli):

```markdown
## Dış servis koruması (Faz 5)

Claude (AI), iyzico ve FaturaEntegratör çağrıları devre kesiciyle korunur: son 10 çağrının yarısı kesinti (bağlantı hatası, zaman aşımı, 5xx, 401/402/403/429) ya da çok yavaşsa devre **açılır** ve 30 saniye boyunca o servis hiç çağrılmaz.

- **AI:** devre açıkken hekim hemen "AI bağlantısı yok" taslağını görür (kota hakkı iade edilir). Aynı anda en fazla 4 AI çağrısı yapılır; 5.'si en fazla 10 sn bekler, yer açılmazsa "AI şu an yoğun" mesajı (503) döner. Cevap bekleme sınırı 60 sn.
- **iyzico:** devre açıkken ödeme başlatma hemen "Ödeme sistemine şu an ulaşılamıyor" (503) döner. Ödeme sonucu sorgusu (kullanıcı iyzico'dan dönerken) devre kesicisiz, kesintide 3 kez denenir.
- **FaturaEntegratör:** devre açıkken gönderim yapılmaz, kuyruk normal takvimle (2 dk / 10 dk / 1 sa / 6 sa) tekrar dener. Durum sorgusu kesintide 3 kez denenir; devre açıksa fatura "işleniyor" durumunda kalır (başarısız işaretlenmez).
- Fatura kesme ve ödeme başlatma gibi **yazma** çağrıları asla otomatik tekrar denenmez (çift kayıt riski).

**Log:** devre açılınca `Devre kesici ACIK: <ad>` (WARN), kapanınca `Devre kesici kapandi: <ad>` (INFO):

    sudo journalctl -u vetly-backend --since "1 hour ago" | grep "Devre kesici"

**Metrik (8081):** `curl -s 127.0.0.1:8081/actuator/prometheus | grep resilience4j_circuitbreaker_state` — `state="open"` satırı 1 ise o devre açık.

**Ayarlar:** `application.yml` → `resilience4j.*` (env ile ezilebilir, ör. `RESILIENCE4J_BULKHEAD_INSTANCES_AI_MAXCONCURRENTCALLS=6`).
```

(Metrik adı Task 1'de farklı çıktıysa gerçek adı yaz.)

- [ ] **Step 3: Yol haritası** — Faz 5 başlığını `## Faz 5 — Resilience (Resilience4j) ✅` yap, `← **SIRADAKİ**`'yi kaldır, altına `- ✅ 2026-10-16: devre kesiciler (ai, payment, efatura), AI bulkhead (4 / 10 sn), okuma retry'ları, BackoffSchedule, OutboundHttp tekilleştirme (spec docs/superpowers/specs/2026-10-16-resilience-faz5-design.md).`; Faz 6 başlığına `← **SIRADAKİ**` ekle.

- [ ] **Step 4: Tam doğrulama** — backend `./mvnw -q test` (toplamı surefire XML'lerinden; Faz 3b sonunda 793) ve `./mvnw -q dependency:tree -Dincludes=org.slf4j:slf4j-api`; frontend `npm run build`. Ham özet satırlarını rapora yapıştır. Kırmızı varsa ürün koduna dokunmadan raporla.
