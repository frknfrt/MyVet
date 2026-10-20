# Gözlemlenebilirlik ve Loglama (Faz 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** UptimeRobot'un yoklayabileceği iki health ucu (`readiness`, `external`), az sayıda iş metriği, ayarla açılan JSON log ve iki katmanlı kişisel veri maskelemesi.

**Architecture:** Yeni `com.vetos.platform.observability` paketi (`@NamedInterface("observability")`): `IntegrationOutcomes` dış servis çağrı sonuçlarını bellekte (30 dk) tutar, hem `vetly.integration.calls` sayacını hem de pasif `HealthIndicator`'ları besler; `LogMasking` satır bazında maskeler. Actuator ayrı porta (`127.0.0.1:8081`) taşınır. 11 haneli sayı güvenlik ağı düz metinde logback `%replace`, JSON'da Spring Boot `StructuredLoggingJsonMembersCustomizer` ile uygulanır.

**Tech Stack:** Java 21, Spring Boot 3.5.16 (Actuator, Micrometer Prometheus, yerleşik structured logging), Spring Modulith, JUnit 5, AssertJ, Mockito, `com.sun.net.httpserver` stub sunucuları.

**Spec:** `docs/superpowers/specs/2026-10-14-gozlemlenebilirlik-faz3-design.md`

## Global Constraints

- **Git:** Commit YOK, push YOK. Tüm değişiklikler working tree'de (IntelliJ changelist) kalır. Hiçbir görevde commit adımı yoktur.
- Backend komutları `backend/` içinde: `./mvnw -q test -Dtest=<Sinif>`; sonuç `target/surefire-reports/TEST-*.xml`'den okunur. `@SpringBootTest` testleri çalışan docker Postgres'i (5433) ister; geliştirme DB'si :8080'deki backend ile ortak; bu fazda migration yok.
- Pencere: **30 dakika**; `DOWN` kuralı: pencerede **≥ 3 deneme ve 0 başarı**; entegrasyon başına en fazla **1000** kayıt.
- Entegrasyon adları (tam yazım): `efatura`, `sms`, `whatsapp`, `ai`, `payment`, `tarbil`. Health'e girenler: `efatura,sms,whatsapp,ai,payment` (tarbil hariç).
- Metrik adları: `vetly.integration.calls` (etiket `integration`, `outcome`=`success`/`failure`), `vetly.ai.duration` (`task`=`soap`/`diagnosis`/`treatment`, `provider`=`ollama`/`claude`), `vetly.queue.pending`, `vetly.queue.failed` (`queue`). Etiketlerde tenantId YOK. Ortak etiket `application=vetly`.
- Yönetim portu: `${MANAGEMENT_PORT:8081}`, adres `127.0.0.1`. Testlerde `management.server.port=0`.
- Log formatı ortam değişkeni: `LOG_FORMAT` (boş → düz metin, `ecs` → JSON).
- Maskeleme biçimleri: telefon `05321234567` → `053******67`; e-posta `furkan@gmail.com` → `f*****@gmail.com`; gövde ilk **300** karakter + `…(+N karakter)`; 11 haneli sayı `12345678901` → `123******01`.
- "Servis erişilemiyor" (= `failure`) sayılan HTTP durumları: `>= 500`, `401`, `402`, `403`, `429`, bağlantı/zaman aşımı hataları. Diğer 4xx (ör. 400, 404, 409, 422) iş hatasıdır → `success`. Simüle/mock moddaki adaptörler kayıt tutmaz.
- Yorum/log dili mevcut kodla aynı: ASCII Türkçe yorumlar (ş/ğ yerine s/g), mevcut yorum yoğunluğu.

## Review Focus

1. **Yerelde çalışan backend ile port çakışması:** `@SpringBootTest(RANDOM_PORT)` testi 8081'i bağlamaya çalışırsa :8080 backend açıkken kırılır → Task 3 testi `management.server.port=0` ile koşar ve `@LocalManagementPort` kullanır.
2. **Yönetim portunda ana güvenlik zincirinin 401 dönmesi:** Spring Security filtre zinciri yönetim bağlamına da uygulanabilir; scrape kimliksiz yapılır → Task 3 testi kimliksiz `GET /actuator/prometheus` ve health uçlarının 200 döndüğünü doğrular.
3. **Güvenlik ağının PII olmayan sayıları bozması:** 10 ve 12 haneli sayılar, `ABC2026000000001` fatura numarası, UUID'ler dokunulmadan kalmalı → Task 1 `LogMaskingTest` bunları sabitler; Task 3 aynı regex'in logback deseninde de aynı sonucu verdiğini uçtan uca test eder.
4. **Gauge sorgusunun DB hatasında scrape'i kırması:** repository istisna atarsa `/actuator/prometheus` yine 200 dönmeli, gauge `NaN` olmalı → Task 6 testi istisna atan mock ile `NaN` doğrular.
5. **Async iş parçacıklarından eşzamanlı `record` çağrıları:** bildirim/e-fatura executor'ları paralel çalışır; kayıt kaybı veya `ConcurrentModificationException` olmamalı → Task 2'de 8 iş parçacığı × 100 kayıt testi.

---

### Task 1: `LogMasking` ve `observability` paketi

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/observability/package-info.java`
- Create: `backend/src/main/java/com/vetos/platform/observability/LogMasking.java`
- Test: `backend/src/test/java/com/vetos/platform/observability/LogMaskingTest.java`

**Interfaces:**
- Produces: `LogMasking.phone(String): String`, `LogMasking.email(String): String`, `LogMasking.body(String): String`, `LogMasking.elevenDigits(String): String` (hepsi `public static`, `null` → `null`). `LogMasking.ELEVEN_DIGIT_REGEX` (`public static final String`) ve `LogMasking.ELEVEN_DIGIT_REPLACEMENT`.

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.platform.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskingTest {

    @Test
    void should_maskPhone_keepingFirstThreeAndLastTwoDigits() {
        assertThat(LogMasking.phone("05321234567")).isEqualTo("053******67");
        assertThat(LogMasking.phone("+90 532 123 45 67")).isEqualTo("905*******67");
    }

    @Test
    void should_maskShortOrNullPhone() {
        assertThat(LogMasking.phone("1234")).isEqualTo("***");
        assertThat(LogMasking.phone("")).isEqualTo("***");
        assertThat(LogMasking.phone(null)).isNull();
    }

    @Test
    void should_maskEmail_keepingFirstLetterAndDomain() {
        assertThat(LogMasking.email("furkan@gmail.com")).isEqualTo("f*****@gmail.com");
        assertThat(LogMasking.email("a@b.com")).isEqualTo("a@b.com");
        assertThat(LogMasking.email("gecersiz")).isEqualTo("***");
        assertThat(LogMasking.email("@b.com")).isEqualTo("***");
        assertThat(LogMasking.email(null)).isNull();
    }

    @Test
    void should_maskElevenDigitNumbers_only() {
        assertThat(LogMasking.elevenDigits("TCKN 12345678901 ok")).isEqualTo("TCKN 123******01 ok");
        assertThat(LogMasking.elevenDigits("tel:05321234567")).isEqualTo("tel:053******67");
        assertThat(LogMasking.elevenDigits("on hane 1234567890")).isEqualTo("on hane 1234567890");
        assertThat(LogMasking.elevenDigits("oniki 123456789012")).isEqualTo("oniki 123456789012");
        assertThat(LogMasking.elevenDigits("fatura ABC2026000000001")).isEqualTo("fatura ABC2026000000001");
        assertThat(LogMasking.elevenDigits("id 3f2a9c4e-1b7d-4e5f-9a0b-123456789012"))
            .isEqualTo("id 3f2a9c4e-1b7d-4e5f-9a0b-123456789012");
        assertThat(LogMasking.elevenDigits(null)).isNull();
    }

    @Test
    void should_truncateAndMaskBody() {
        String longBody = "{\"tckn\":\"12345678901\",\"x\":\"" + "a".repeat(400) + "\"}";

        String masked = LogMasking.body(longBody);

        assertThat(masked).startsWith("{\"tckn\":\"123******01\"");
        assertThat(masked).endsWith("…(+" + (longBody.length() - 300) + " karakter)");
        assertThat(masked).doesNotContain("12345678901");
        assertThat(LogMasking.body("kisa")).isEqualTo("kisa");
        assertThat(LogMasking.body(null)).isNull();
    }
}
```

Not: `"oniki 123456789012"` — 12 haneli dizinin içindeki ilk 11 hane `(?!\d)` yüzünden eşleşmez; UUID'nin son grubu 12 hanedir, aynı nedenle dokunulmaz.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=LogMaskingTest`
Expected: FAIL — derleme hatası, `LogMasking` yok.

- [ ] **Step 3: Write minimal implementation**

`package-info.java`:
```java
@org.springframework.modulith.NamedInterface("observability")
package com.vetos.platform.observability;
```

`LogMasking.java`:
```java
package com.vetos.platform.observability;

import java.util.regex.Pattern;

/**
 * Loglara kisisel veri (telefon, e-posta, TCKN) acik yazilmasin diye satir bazinda
 * maskeleme yardimcilari (spec 2026-10-14 S4.5, 1. katman). LogPiiGuardTest, telefon/
 * e-posta/yanit govdesi tasiyan log argumanlarinin bu siniftan gecmesini zorunlu kilar.
 * elevenDigits ayni zamanda 2. katmanin (logback %replace, JSON customizer) tek tanimidir.
 */
public final class LogMasking {

    /** 11 haneli, oncesi/sonrasi rakam olmayan sayi: TCKN ve 05xx cep telefonu. */
    public static final String ELEVEN_DIGIT_REGEX = "(?<!\\d)(\\d{3})\\d{6}(\\d{2})(?!\\d)";
    public static final String ELEVEN_DIGIT_REPLACEMENT = "$1******$2";
    static final int BODY_LIMIT = 300;

    private static final Pattern ELEVEN_DIGITS = Pattern.compile(ELEVEN_DIGIT_REGEX);

    private LogMasking() {
    }

    public static String phone(String raw) {
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() < 5) {
            return "***";
        }
        return digits.substring(0, 3) + "*".repeat(digits.length() - 5) + digits.substring(digits.length() - 2);
    }

    public static String email(String raw) {
        if (raw == null) {
            return null;
        }
        int at = raw.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return raw.charAt(0) + "*".repeat(at - 1) + raw.substring(at);
    }

    /** Dis servis yanit govdesi: 11 haneli sayilar maskelenir, ilk 300 karakterden fazlasi kesilir. */
    public static String body(String raw) {
        if (raw == null) {
            return null;
        }
        String masked = elevenDigits(raw);
        if (masked.length() <= BODY_LIMIT) {
            return masked;
        }
        return masked.substring(0, BODY_LIMIT) + "…(+" + (masked.length() - BODY_LIMIT) + " karakter)";
    }

    public static String elevenDigits(String text) {
        if (text == null || countDigits(text) < 11) {
            return text;
        }
        return ELEVEN_DIGITS.matcher(text).replaceAll(ELEVEN_DIGIT_REPLACEMENT);
    }

    private static int countDigits(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (Character.isDigit(text.charAt(i))) {
                count++;
            }
        }
        return count;
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q test -Dtest=LogMaskingTest`
Expected: PASS (5/5).

---

### Task 2: `IntegrationOutcomes` ve pasif health göstergeleri

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/observability/IntegrationOutcomes.java`
- Create: `backend/src/main/java/com/vetos/platform/observability/ObservabilityConfig.java`
- Test: `backend/src/test/java/com/vetos/platform/observability/IntegrationOutcomesTest.java`

**Interfaces:**
- Consumes: —
- Produces:
  - `public IntegrationOutcomes(MeterRegistry registry, Clock clock)`
  - sabitler `IntegrationOutcomes.EFATURA|SMS|WHATSAPP|AI|PAYMENT|TARBIL` (`String`)
  - `void record(String integration, boolean success)`
  - `<T> T recordAiCall(String task, String provider, Callable<T> call) throws Exception`
  - `Health health(String integration)` (`org.springframework.boot.actuate.health.Health`)
  - `static boolean isOutageStatus(int httpStatus)`
  - Spring bean'leri: `IntegrationOutcomes`, `HealthIndicator` bean adları `efaturaHealthIndicator`, `smsHealthIndicator`, `whatsappHealthIndicator`, `aiHealthIndicator`, `paymentHealthIndicator` (Actuator'da `efatura`, `sms`, `whatsapp`, `ai`, `payment` olarak görünür).

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IntegrationOutcomesTest {

    /** Testte ilerletilebilen saat. */
    static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-14T10:00:00Z");

        void advance(Duration d) { now = now.plus(d); }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MutableClock clock = new MutableClock();
    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(registry, clock);

    private double calls(String integration, String outcome) {
        Counter c = registry.find("vetly.integration.calls").tag("integration", integration).tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }

    @Test
    void should_beUp_when_noAttempts() {
        assertThat(outcomes.health(IntegrationOutcomes.EFATURA).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_beDown_when_threeFailuresAndNoSuccess() {
        outcomes.record(IntegrationOutcomes.SMS, false);
        outcomes.record(IntegrationOutcomes.SMS, false);
        assertThat(outcomes.health(IntegrationOutcomes.SMS).getStatus()).isEqualTo(Status.UP);

        outcomes.record(IntegrationOutcomes.SMS, false);
        assertThat(outcomes.health(IntegrationOutcomes.SMS).getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void should_beUp_when_anySuccessInWindow() {
        outcomes.record(IntegrationOutcomes.SMS, true);
        outcomes.record(IntegrationOutcomes.SMS, false);
        outcomes.record(IntegrationOutcomes.SMS, false);
        outcomes.record(IntegrationOutcomes.SMS, false);

        assertThat(outcomes.health(IntegrationOutcomes.SMS).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_forgetAttemptsOlderThanThirtyMinutes() {
        outcomes.record(IntegrationOutcomes.PAYMENT, true);
        clock.advance(Duration.ofMinutes(31));
        outcomes.record(IntegrationOutcomes.PAYMENT, false);
        outcomes.record(IntegrationOutcomes.PAYMENT, false);
        outcomes.record(IntegrationOutcomes.PAYMENT, false);

        assertThat(outcomes.health(IntegrationOutcomes.PAYMENT).getStatus()).isEqualTo(Status.DOWN);

        clock.advance(Duration.ofMinutes(31));
        assertThat(outcomes.health(IntegrationOutcomes.PAYMENT).getStatus()).isEqualTo(Status.UP);
    }

    @Test
    void should_keepAtMostThousandAttempts() {
        outcomes.record(IntegrationOutcomes.AI, true);
        for (int i = 0; i < 1000; i++) {
            outcomes.record(IntegrationOutcomes.AI, false);
        }
        // tek basari 1000 sinirinin disina itildi
        assertThat(outcomes.health(IntegrationOutcomes.AI).getStatus()).isEqualTo(Status.DOWN);
    }

    @Test
    void should_countCallsWithIntegrationAndOutcomeTags() {
        outcomes.record(IntegrationOutcomes.WHATSAPP, true);
        outcomes.record(IntegrationOutcomes.WHATSAPP, false);
        outcomes.record(IntegrationOutcomes.TARBIL, true);

        assertThat(calls("whatsapp", "success")).isEqualTo(1);
        assertThat(calls("whatsapp", "failure")).isEqualTo(1);
        assertThat(calls("tarbil", "success")).isEqualTo(1);
    }

    @Test
    void should_timeAiCallAndRecordOutcome() throws Exception {
        String result = outcomes.recordAiCall("soap", "claude", () -> "ok");
        assertThatThrownBy(() -> outcomes.recordAiCall("soap", "claude", () -> { throw new IllegalStateException("x"); }))
            .isInstanceOf(IllegalStateException.class);

        assertThat(result).isEqualTo("ok");
        Timer timer = registry.find("vetly.ai.duration").tag("task", "soap").tag("provider", "claude").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(2);
        assertThat(calls("ai", "success")).isEqualTo(1);
        assertThat(calls("ai", "failure")).isEqualTo(1);
    }

    @Test
    void should_classifyOutageStatuses() {
        assertThat(IntegrationOutcomes.isOutageStatus(500)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(503)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(401)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(402)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(403)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(429)).isTrue();
        assertThat(IntegrationOutcomes.isOutageStatus(400)).isFalse();
        assertThat(IntegrationOutcomes.isOutageStatus(404)).isFalse();
        assertThat(IntegrationOutcomes.isOutageStatus(422)).isFalse();
    }

    @Test
    void should_notLoseRecords_when_calledConcurrently() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<?>> futures = new ArrayList<>();
        for (int t = 0; t < 8; t++) {
            futures.add(pool.submit(() -> {
                for (int i = 0; i < 100; i++) {
                    outcomes.record(IntegrationOutcomes.EFATURA, false);
                }
            }));
        }
        for (Future<?> f : futures) {
            f.get();
        }
        pool.shutdown();

        assertThat(calls("efatura", "failure")).isEqualTo(800);
        assertThat(outcomes.health(IntegrationOutcomes.EFATURA).getDetails()).containsEntry("attempts", 800);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=IntegrationOutcomesTest`
Expected: FAIL — derleme hatası, `IntegrationOutcomes` yok.

- [ ] **Step 3: Write minimal implementation**

`IntegrationOutcomes.java`:
```java
package com.vetos.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.actuate.health.Health;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dis servis cagrilarinin sonucunu bellekte (son 30 dk) tutar; hem vetly.integration.calls
 * sayacini hem de "external" health grubunu besler (spec 2026-10-14 S4.1-4.3).
 * Pasif kontrol: dis servise test istegi atilmaz, sadece gercek cagrilarin sonucuna bakilir.
 * Yeniden baslatmada bellek sifirlanir -- deneme yoksa UP sayilir (bilincli).
 */
@Slf4j
public class IntegrationOutcomes {

    public static final String EFATURA = "efatura";
    public static final String SMS = "sms";
    public static final String WHATSAPP = "whatsapp";
    public static final String AI = "ai";
    public static final String PAYMENT = "payment";
    public static final String TARBIL = "tarbil";

    static final Duration WINDOW = Duration.ofMinutes(30);
    static final int MIN_ATTEMPTS = 3;
    static final int MAX_ENTRIES = 1000;

    private record Attempt(Instant at, boolean success) {
    }

    private final MeterRegistry registry;
    private final Clock clock;
    private final Map<String, Deque<Attempt>> attempts = new ConcurrentHashMap<>();
    private final Map<String, Boolean> downState = new ConcurrentHashMap<>();

    public IntegrationOutcomes(MeterRegistry registry, Clock clock) {
        this.registry = registry;
        this.clock = clock;
    }

    /**
     * "Servis erisilemiyor" sayilan HTTP durumlari: 5xx, kimlik/yetki (401, 403), bakiye (402),
     * kota (429). Diger 4xx (dogrulama hatasi vb.) is hatasidir -- servis ayakta.
     */
    public static boolean isOutageStatus(int httpStatus) {
        return httpStatus >= 500 || httpStatus == 401 || httpStatus == 402 || httpStatus == 403 || httpStatus == 429;
    }

    public void record(String integration, boolean success) {
        registry.counter("vetly.integration.calls",
            "integration", integration, "outcome", success ? "success" : "failure").increment();
        Deque<Attempt> deque = attempts.computeIfAbsent(integration, k -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(new Attempt(clock.instant(), success));
            while (deque.size() > MAX_ENTRIES) {
                deque.removeFirst();
            }
            pruneOld(deque);
        }
    }

    /** AI cagrisini sarar: sureyi vetly.ai.duration'a, sonucu "ai" entegrasyonuna yazar; istisnayi aynen firlatir. */
    public <T> T recordAiCall(String task, String provider, Callable<T> call) throws Exception {
        Timer.Sample sample = Timer.start(registry);
        boolean success = false;
        try {
            T result = call.call();
            success = true;
            return result;
        } finally {
            sample.stop(Timer.builder("vetly.ai.duration").tag("task", task).tag("provider", provider).register(registry));
            record(AI, success);
        }
    }

    public Health health(String integration) {
        int total = 0;
        int successes = 0;
        Deque<Attempt> deque = attempts.get(integration);
        if (deque != null) {
            synchronized (deque) {
                pruneOld(deque);
                total = deque.size();
                successes = (int) deque.stream().filter(Attempt::success).count();
            }
        }
        boolean down = total >= MIN_ATTEMPTS && successes == 0;
        Boolean wasDown = downState.put(integration, down);
        if (down && !Boolean.TRUE.equals(wasDown)) {
            log.warn("Entegrasyon DOWN: {} -- son {} dakikada {} denemenin hicbiri basarili degil",
                integration, WINDOW.toMinutes(), total);
        } else if (!down && Boolean.TRUE.equals(wasDown)) {
            log.info("Entegrasyon duzeldi: {}", integration);
        }
        return (down ? Health.down() : Health.up())
            .withDetail("attempts", total)
            .withDetail("successes", successes)
            .build();
    }

    private void pruneOld(Deque<Attempt> deque) {
        Instant cutoff = clock.instant().minus(WINDOW);
        while (!deque.isEmpty() && deque.peekFirst().at().isBefore(cutoff)) {
            deque.removeFirst();
        }
    }
}
```

`ObservabilityConfig.java`:
```java
package com.vetos.platform.observability;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Bean adlarindaki "HealthIndicator" soneki Actuator'da atilir: efaturaHealthIndicator -> "efatura".
 * Bu adlar application.yml'deki management.endpoint.health.group.external.include listesiyle ayni olmali.
 * TARBIL bilerek yok (spec S2: gonderim hekimin tarayicisinda).
 */
@Configuration
class ObservabilityConfig {

    @Bean
    IntegrationOutcomes integrationOutcomes(MeterRegistry registry) {
        return new IntegrationOutcomes(registry, Clock.systemUTC());
    }

    @Bean
    HealthIndicator efaturaHealthIndicator(IntegrationOutcomes outcomes) {
        return () -> outcomes.health(IntegrationOutcomes.EFATURA);
    }

    @Bean
    HealthIndicator smsHealthIndicator(IntegrationOutcomes outcomes) {
        return () -> outcomes.health(IntegrationOutcomes.SMS);
    }

    @Bean
    HealthIndicator whatsappHealthIndicator(IntegrationOutcomes outcomes) {
        return () -> outcomes.health(IntegrationOutcomes.WHATSAPP);
    }

    @Bean
    HealthIndicator aiHealthIndicator(IntegrationOutcomes outcomes) {
        return () -> outcomes.health(IntegrationOutcomes.AI);
    }

    @Bean
    HealthIndicator paymentHealthIndicator(IntegrationOutcomes outcomes) {
        return () -> outcomes.health(IntegrationOutcomes.PAYMENT);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend && ./mvnw -q test -Dtest=IntegrationOutcomesTest`
Expected: PASS (9/9).

---

### Task 3: Yönetim portu, health grupları, log formatı ve güvenlik ağı (kablolama)

**Files:**
- Modify: `backend/src/main/resources/application.yml` (`management:` bloğu satır ~40-47; `logging:` bloğu satır ~148-152)
- Modify: `backend/src/main/java/com/vetos/platform/security/SecurityConfig.java:59-65`
- Create: `backend/src/main/java/com/vetos/platform/observability/PiiMaskingJsonMembersCustomizer.java`
- Test: `backend/src/test/java/com/vetos/platform/observability/PiiMaskingJsonMembersCustomizerTest.java`
- Test: `backend/src/test/java/com/vetos/platform/observability/ObservabilityWiringTest.java`

**Interfaces:**
- Consumes: `IntegrationOutcomes` bean ve sabitleri (Task 2), `LogMasking.elevenDigits` (Task 1).
- Produces: yönetim portunda `/actuator/health/readiness`, `/actuator/health/external`, `/actuator/health/liveness`, `/actuator/prometheus`; ana portta `/actuator/**` yok (404). `PiiMaskingJsonMembersCustomizer` (public, argümansız kurucu).

- [ ] **Step 1: Write the failing tests**

`PiiMaskingJsonMembersCustomizerTest.java`:
```java
package com.vetos.platform.observability;

import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonWriter;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskingJsonMembersCustomizerTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void should_maskElevenDigitNumbers_inMessageMemberOnly() {
        JsonWriter<String> writer = JsonWriter.of(members -> {
            members.add("message", (String s) -> s);
            members.add("other", (String s) -> s);
            new PiiMaskingJsonMembersCustomizer().customize((JsonWriter.Members) members);
        });

        String json = writer.writeToString("TCKN 12345678901");

        assertThat(json).contains("\"message\":\"TCKN 123******01\"");
        assertThat(json).contains("\"other\":\"TCKN 12345678901\"");
    }
}
```

`ObservabilityWiringTest.java`:
```java
package com.vetos.platform.observability;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Canli docker-compose Postgres gerekir. management.server.port=0: yerelde :8081'de calisan
 * backend ile cakismasin diye rastgele yonetim portu.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = "management.server.port=0")
@ExtendWith(OutputCaptureExtension.class)
class ObservabilityWiringTest {

    @LocalServerPort
    private int serverPort;

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private IntegrationOutcomes outcomes;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> get(int port, String path) throws Exception {
        return http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void should_serveReadinessAndExternal_onManagementPort_withoutDetails() throws Exception {
        HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");
        HttpResponse<String> external = get(managementPort, "/actuator/health/external");
        HttpResponse<String> liveness = get(managementPort, "/actuator/health/liveness");

        assertThat(readiness.statusCode()).isEqualTo(200);
        assertThat(readiness.body()).contains("\"status\":\"UP\"").doesNotContain("components");
        assertThat(external.statusCode()).isEqualTo(200);
        assertThat(external.body()).contains("\"status\":\"UP\"").doesNotContain("components");
        assertThat(liveness.statusCode()).isEqualTo(200);
    }

    @Test
    void should_reportExternalDown_when_integrationFailsRepeatedly_andRecover() throws Exception {
        outcomes.record(IntegrationOutcomes.PAYMENT, false);
        outcomes.record(IntegrationOutcomes.PAYMENT, false);
        outcomes.record(IntegrationOutcomes.PAYMENT, false);

        HttpResponse<String> down = get(managementPort, "/actuator/health/external");
        HttpResponse<String> readiness = get(managementPort, "/actuator/health/readiness");

        outcomes.record(IntegrationOutcomes.PAYMENT, true); // paylasilan context'i temiz birak
        HttpResponse<String> up = get(managementPort, "/actuator/health/external");

        assertThat(down.statusCode()).isEqualTo(503);
        assertThat(down.body()).contains("\"status\":\"DOWN\"");
        assertThat(readiness.statusCode()).isEqualTo(200); // dis servis readiness'i etkilemez
        assertThat(up.statusCode()).isEqualTo(200);
    }

    @Test
    void should_exposeVetlyMetrics_onManagementPortOnly() throws Exception {
        outcomes.record(IntegrationOutcomes.TARBIL, true);

        HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus");
        HttpResponse<String> onMainPort = get(serverPort, "/actuator/prometheus");

        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body()).contains("vetly_integration_calls_total").contains("application=\"vetly\"");
        assertThat(onMainPort.statusCode()).isEqualTo(404);
    }

    @Test
    void should_maskElevenDigitNumbers_inPlainTextConsoleLog(CapturedOutput output) {
        LoggerFactory.getLogger(ObservabilityWiringTest.class).info("TCKN kontrol 12345678901 bitti");

        assertThat(output.getOut()).contains("TCKN kontrol 123******01 bitti");
        assertThat(output.getOut()).doesNotContain("12345678901");
        // LOG_FORMAT bos -> duz metin (JSON degil) ve requestId/tenantId alani duruyor
        assertThat(output.getOut()).contains("[etc./sistem]");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest='PiiMaskingJsonMembersCustomizerTest,ObservabilityWiringTest'`
Expected: FAIL — `PiiMaskingJsonMembersCustomizer` yok (derleme hatası). (Customizer'ı yazdıktan sonra wiring testi de kırmızı kalmalı: `@LocalManagementPort` çözülmez / 8080 ile aynı porta düşer, `external` grubu yok → 404.)

- [ ] **Step 3: Write the implementation**

`PiiMaskingJsonMembersCustomizer.java`:
```java
package com.vetos.platform.observability;

import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.logging.structured.StructuredLoggingJsonMembersCustomizer;

/**
 * LOG_FORMAT=ecs (JSON log) iken 2. katman guvenlik agi: "message" alanindaki 11 haneli
 * sayilari maskeler (spec S4.5). Duz metin modunda ayni regex logback %replace ile uygulanir
 * (application.yml logging.pattern.console).
 */
public class PiiMaskingJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {

    @Override
    public void customize(JsonWriter.Members<Object> members) {
        members.applyingValueProcessor(
            JsonWriter.ValueProcessor.of(String.class, LogMasking::elevenDigits).whenHasPath("message"));
    }
}
```

`application.yml` — mevcut `management:` bloğunun tamamını şununla değiştir:
```yaml
management:
  # Actuator ayri portta ve sadece localhost'ta (spec 2026-10-14 S4.2). Disariya nginx yalnizca
  # /health/ready ve /health/external'i acar; metrikler sadece sunucunun icinden okunur.
  server:
    port: ${MANAGEMENT_PORT:8081}
    address: 127.0.0.1
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  endpoint:
    health:
      show-details: never
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState,db
        # Pasif dis servis kontrolu (IntegrationOutcomes). Readiness'a bilerek girmez:
        # iyzico dustu diye uygulama "hazir degil" sayilmamali.
        external:
          include: efatura,sms,whatsapp,ai,payment
  metrics:
    tags:
      application: vetly
```

`application.yml` — mevcut `logging:` bloğunu (`level` + `pattern.correlation`) şununla değiştir:
```yaml
logging:
  level:
    com.vetos: debug
  # LOG_FORMAT=ecs -> Spring Boot yerlesik JSON (ECS); bos -> asagidaki duz metin deseni.
  structured:
    format:
      console: ${LOG_FORMAT:}
    json:
      customizer: com.vetos.platform.observability.PiiMaskingJsonMembersCustomizer
  pattern:
    # Spring Boot varsayilan deseni + requestId/tenantId + mesajdaki 11 haneli sayilarin
    # maskelenmesi (regex LogMasking.ELEVEN_DIGIT_REGEX ile ayni; ObservabilityWiringTest dogrular).
    console: >-
      %clr(%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX}){faint} %clr(%5p) %clr([%15.15t]){faint}
      [%X{requestId:-etc.}/%X{tenantId:-sistem}] %clr(%-40.40logger{39}){cyan} %clr(:){faint}
      %replace(%m){'(?<!\d)(\d{3})\d{6}(\d{2})(?!\d)', '$1******$2'}%n%wEx
```
(`>-` katlanmış blok: satır sonları tek boşluğa dönüşür, ters bölü kaçışı gerekmez.)

`SecurityConfig.java` — şu iki kuralı ve aradaki yorumu sil:
```java
                .requestMatchers("/actuator/health").permitAll()
                // metrics/prometheus, tenant'lar arasi operasyonel bilgi sizdiriyor (tum
                // endpoint envanteri, tum kiracilarin gecikme/hata sayilari, JVM ici bilgi)
                // -- herhangi bir personel hesabiyla degil, ayri bir scrape kimlik
                // bilgisiyle korunmali (henuz tasarlanmadi, bkz. takip notu). Su an icin
                // tamamen kapali.
                .requestMatchers("/actuator/prometheus", "/actuator/metrics/**").denyAll()
```
yerine:
```java
                // Actuator yalnizca 127.0.0.1:8081 yonetim portunda (application.yml
                // management.server); guvenlik ag seviyesinde. Bu zincir yonetim baglamina da
                // uygulanabildigi icin acikca izin verilir; ana portta bu yollar zaten yok (404).
                .requestMatchers("/actuator/**").permitAll()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='PiiMaskingJsonMembersCustomizerTest,ObservabilityWiringTest,SecurityConfigCorsTest,GlobalExceptionHandlerTest'`
Expected: PASS.

Geri dönüş yolu (sadece gerekirse): `should_maskElevenDigitNumbers_inPlainTextConsoleLog` "[etc./sistem]" bulamaz ve çıktı JSON ise, boş `${LOG_FORMAT:}` structured modu açmış demektir. O zaman:
1. `application.yml`'den `logging.structured.format.console` satırını kaldır (`json.customizer` kalsın).
2. `backend/src/main/resources/application-ecs-log.yml` oluştur:
   ```yaml
   logging:
     structured:
       format:
         console: ecs
   ```
3. Devreye alma notunda JSON açma yolu `LOG_FORMAT=ecs` yerine `SPRING_PROFILES_ACTIVE=prod,ecs-log` olur; Task 8 Step 3'te spec §4.4 ve §7'yi buna göre güncelle.

- [ ] **Step 5: Ana port davranışını elle doğrula (opsiyonel, hızlı)**

Run: `cd backend && grep -rn "actuator" src/main/java src/test/java | grep -v observability`
Expected: `SecurityConfig` dışında `/actuator` referansı yok (eski `permitAll`/`denyAll` testlere bağlı değil).

---

### Task 4: HTTP adaptörleri — sonuç kaydı ve maskeleme (e-fatura, SMS, WhatsApp, platform SMS, iyzico)

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/adapter/FaturaEntegratorEInvoiceGatewayAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/infrastructure/adapter/NotificationProviderAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/IletiMerkeziPlatformBillingSmsAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/IyzicoPaymentGatewayAdapter.java`
- Modify: `package-info.java` of `modules/integration/efatura`, `modules/notification`, `modules/platformadmin` (add `"platform::observability"` to `allowedDependencies`)
- Test: the four matching `*AdapterTest.java` files under `backend/src/test/java/com/vetos/modules/...`

**Interfaces:**
- Consumes: `IntegrationOutcomes` (`record`, `isOutageStatus`, sabitler), `LogMasking.phone/body`.
- Produces: Her adaptörün **test (paket-özel) kurucusu ve `@Autowired` kurucusu son parametre olarak `IntegrationOutcomes outcomes` alır**. Task 7'deki guard testi bu dosyalardaki log satırlarını tarar.

Ortak test yardımcıları (dört test sınıfının her birine, sınıf başına alanlar olarak ekle — diğer alanlardan ÖNCE, çünkü iyzico testinde alan başlatıcıları `outcomes`'u kullanıyor):
```java
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final IntegrationOutcomes outcomes = new IntegrationOutcomes(registry, Clock.systemUTC());

    private double calls(String integration, String outcome) {
        Counter c = registry.find("vetly.integration.calls").tag("integration", integration).tag("outcome", outcome).counter();
        return c == null ? 0 : c.count();
    }
```
importlar: `io.micrometer.core.instrument.Counter`, `io.micrometer.core.instrument.simple.SimpleMeterRegistry`, `com.vetos.platform.observability.IntegrationOutcomes`, `java.time.Clock`.
Bu dosyalardaki **her** `new <Adapter>(...)` çağrısına son argüman olarak `outcomes` eklenir (sayılar: e-fatura testi 4, bildirim testi 1, platform SMS testi 2, iyzico testi 3).

- [ ] **Step 1: Write the failing tests**

`FaturaEntegratorEInvoiceGatewayAdapterTest` — ekle:
```java
    @Test
    void should_recordSuccess_when_providerAccepts() {
        stubResponseBody = "{\"data\":{\"id\":123,\"workflow_status\":\"processing\"}}";

        anAdapter("test-api-key").submit(aRequest());

        assertThat(calls("efatura", "success")).isEqualTo(1);
        assertThat(calls("efatura", "failure")).isZero();
    }

    @Test
    void should_recordSuccess_when_providerRejectsWithValidationError() {
        stubStatusCode = 422;
        stubResponseBody = "{\"message\":\"sale_channel_id gecersiz\"}";

        anAdapter("test-api-key").submit(aRequest());

        assertThat(calls("efatura", "success")).isEqualTo(1);
    }

    @Test
    void should_recordFailure_when_providerReturns5xx() {
        stubStatusCode = 503;
        stubResponseBody = "{\"message\":\"bakim\"}";

        anAdapter("test-api-key").submit(aRequest());

        assertThat(calls("efatura", "failure")).isEqualTo(1);
    }

    @Test
    void should_recordFailure_when_providerUnreachable() {
        FaturaEntegratorEInvoiceGatewayAdapter adapter = anAdapter("test-api-key");
        stubServer.stop(0);

        adapter.submit(aRequest());

        assertThat(calls("efatura", "failure")).isEqualTo(1);
    }

    @Test
    void should_notRecord_when_notConfigured() {
        new FaturaEntegratorEInvoiceGatewayAdapter("", 753L, 1045L, new ObjectMapper(), "http://unused", outcomes).submit(aRequest());

        assertThat(registry.find("vetly.integration.calls").counters()).isEmpty();
    }
```

`NotificationProviderAdapterTest` — ekle:
```java
    @Test
    void should_recordSmsSuccess_when_iletiMerkeziAccepts() {
        adapter("", "test-key", "test-hash").send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Merhaba"));

        assertThat(calls("sms", "success")).isEqualTo(1);
    }

    @Test
    void should_recordSmsFailure_when_iletiMerkeziRejectsCredentials() {
        iletiMerkeziStubResponse = "{\"response\":{\"status\":{\"code\":401,\"message\":\"Kimlik dogrulama basarisiz\"}}}";

        adapter("", "bad-key", "bad-hash").send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Merhaba"));

        assertThat(calls("sms", "failure")).isEqualTo(1);
    }

    @Test
    void should_recordWhatsappSuccess_when_metaAccepts() {
        adapter("meta-token", "", "").send(new NotificationSendRequest(NotificationChannel.WHATSAPP, "05551234567", "Merhaba"));

        assertThat(calls("whatsapp", "success")).isEqualTo(1);
    }

    @Test
    void should_recordWhatsappFailure_when_metaUnreachable() {
        NotificationProviderAdapter adapter = adapter("meta-token", "", "");
        stubServer.stop(0);

        adapter.send(new NotificationSendRequest(NotificationChannel.WHATSAPP, "05551234567", "Merhaba"));

        assertThat(calls("whatsapp", "failure")).isEqualTo(1);
    }

    @Test
    void should_notRecord_when_simulated() {
        adapter("", "", "").send(new NotificationSendRequest(NotificationChannel.SMS, "05551234567", "Merhaba"));

        assertThat(registry.find("vetly.integration.calls").counters()).isEmpty();
    }
```

`IletiMerkeziPlatformBillingSmsAdapterTest` — mevcut yardımcılar `configuredAdapter()` (satır 64) ve `unconfiguredAdapter()` (satır 68); ikisinin `new` çağrısına `outcomes` ekle. Yeni testler:
```java
    @Test
    void should_recordSmsSuccess_when_gatewayAccepts() {
        configuredAdapter().sendTenantSuspended("Test Klinik", "05551234567");

        assertThat(calls("sms", "success")).isEqualTo(1);
    }

    @Test
    void should_recordSmsFailure_when_gatewayRejectsCredentialsInBody() {
        stubResponse = "{\"response\":{\"status\":{\"code\":401,\"message\":\"Kimlik dogrulama basarisiz\"}}}";

        configuredAdapter().sendTenantSuspended("Test Klinik", "05551234567");

        assertThat(calls("sms", "failure")).isEqualTo(1);
    }
```

`IyzicoPaymentGatewayAdapterTest` — ekle:
```java
    @Test
    void should_recordPaymentFailure_when_gatewayUnreachable() {
        assertThatThrownBy(() -> unreachableConfiguredAdapter.initializeCheckout(
            UUID.randomUUID().toString(), new BigDecimal("500.00"), "Test Klinik", "test@example.com"))
            .isInstanceOf(PaymentGatewayException.class);

        assertThat(calls("payment", "failure")).isEqualTo(1);
    }

    @Test
    void should_recordPaymentSuccess_when_gatewayAnswersEvenWithBusinessFailure() {
        stubResponseBody = "{\"status\":\"failure\",\"errorCode\":\"5007\",\"errorMessage\":\"Tutar gecersiz\"}";

        assertThatThrownBy(() -> stubbedConfiguredAdapter.initializeCheckout(
            UUID.randomUUID().toString(), new BigDecimal("500.00"), "Test Klinik", "test@example.com"))
            .isInstanceOf(PaymentGatewayException.class);

        assertThat(calls("payment", "success")).isEqualTo(1);
    }

    @Test
    void should_notRecord_when_simulated() {
        adapter.initializeCheckout(UUID.randomUUID().toString(), new BigDecimal("500.00"), "Test Klinik", "test@example.com");

        assertThat(registry.find("vetly.integration.calls").counters()).isEmpty();
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest='FaturaEntegratorEInvoiceGatewayAdapterTest,NotificationProviderAdapterTest,IletiMerkeziPlatformBillingSmsAdapterTest,IyzicoPaymentGatewayAdapterTest'`
Expected: FAIL — derleme hatası (kuruculara `outcomes` argümanı fazla).

- [ ] **Step 3: Implement — `FaturaEntegratorEInvoiceGatewayAdapter`**

Alan ve kurucular:
```java
    private final IntegrationOutcomes outcomes;

    @Autowired
    FaturaEntegratorEInvoiceGatewayAdapter(
        @Value("${efatura.faturaentegrator.api-key:}") String apiKey,
        @Value("${efatura.faturaentegrator.invoice-integration-id:0}") long invoiceIntegrationId,
        @Value("${efatura.faturaentegrator.sale-channel-id:0}") long saleChannelId,
        ObjectMapper objectMapper,
        IntegrationOutcomes outcomes
    ) {
        this(apiKey, invoiceIntegrationId, saleChannelId, objectMapper, "https://app.faturaentegrator.com/api", outcomes);
    }

    FaturaEntegratorEInvoiceGatewayAdapter(
        String apiKey, long invoiceIntegrationId, long saleChannelId, ObjectMapper objectMapper, String apiBaseUrl,
        IntegrationOutcomes outcomes
    ) {
        // mevcut govde aynen + son satir:
        this.outcomes = outcomes;
    }
```
`submit` içindeki try/catch:
```java
            if (providerReference == null || providerReference.isBlank()) {
                outcomes.record(IntegrationOutcomes.EFATURA, false);
                log.warn("faturaentegrator beklenmeyen yanit dondu (data.id yok): {}", LogMasking.body(raw));
                return EInvoiceSubmissionOutcome.failure("faturaentegrator yaniti beklenmeyen bicimde (data.id eksik)");
            }
            outcomes.record(IntegrationOutcomes.EFATURA, true);
            return EInvoiceSubmissionOutcome.pending(/* mevcut argumanlar aynen */);
        } catch (RestClientResponseException e) {
            outcomes.record(IntegrationOutcomes.EFATURA, !IntegrationOutcomes.isOutageStatus(e.getStatusCode().value()));
            log.warn("faturaentegrator gonderimi basarisiz: durum={}, govde={}", e.getStatusCode(), LogMasking.body(e.getResponseBodyAsString()));
            return EInvoiceSubmissionOutcome.failure(/* mevcut mesaj aynen */);
        } catch (Exception e) {
            outcomes.record(IntegrationOutcomes.EFATURA, false);
            log.warn("faturaentegrator gonderimi basarisiz: {}", e.getMessage());
            return EInvoiceSubmissionOutcome.failure("faturaentegrator cagrisi basarisiz: " + e.getMessage());
        }
```
`fetchStatus`: `raw` okunup JSON parse edildikten hemen sonra (`JsonNode data = ...` satırından önce) `outcomes.record(IntegrationOutcomes.EFATURA, true);`; `RestClientResponseException` catch'inde `outcomes.record(IntegrationOutcomes.EFATURA, !IntegrationOutcomes.isOutageStatus(e.getStatusCode().value()));` ve log argümanı `LogMasking.body(e.getResponseBodyAsString())`; genel `Exception` catch'inde `outcomes.record(IntegrationOutcomes.EFATURA, false);`.
Importlar: `com.vetos.platform.observability.IntegrationOutcomes`, `com.vetos.platform.observability.LogMasking`.

- [ ] **Step 4: Implement — `NotificationProviderAdapter`**

Alan `private final IntegrationOutcomes outcomes;`; `@Autowired` kurucunun parametre listesinin sonuna `IntegrationOutcomes outcomes` ekle ve `this(...)` çağrısının sonuna `outcomes` geçir; paket-özel kurucunun parametre listesinin sonuna `IntegrationOutcomes outcomes` ekle, gövdeye `this.outcomes = outcomes;`.

`sendWhatsAppViaMeta` try/catch:
```java
            outcomes.record(IntegrationOutcomes.WHATSAPP, true);
            log.info("WhatsApp gonderimi (Meta): alici={}, yanit={}", LogMasking.phone(request.recipientContact()), LogMasking.body(responseBody));
            return NotificationSendOutcome.success("Meta WhatsApp Cloud API'ye iletildi");
        } catch (RestClientResponseException e) {
            outcomes.record(IntegrationOutcomes.WHATSAPP, !IntegrationOutcomes.isOutageStatus(e.getStatusCode().value()));
            log.warn(
                "Meta WhatsApp gonderimi basarisiz: alici={}, durum={}, govde={}",
                LogMasking.phone(request.recipientContact()), e.getStatusCode(), LogMasking.body(e.getResponseBodyAsString())
            );
            return NotificationSendOutcome.failure("Meta hatasi (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            outcomes.record(IntegrationOutcomes.WHATSAPP, false);
            log.warn("Meta WhatsApp gonderimi basarisiz: alici={}, hata={}", LogMasking.phone(request.recipientContact()), e.getMessage());
            return NotificationSendOutcome.failure("Saglayiciya ulasilamadi: " + e.getMessage());
        }
```
`sendSmsViaIletiMerkezi`:
```java
            if (code == 200) {
                outcomes.record(IntegrationOutcomes.SMS, true);
                log.info("SMS gonderimi (Ileti Merkezi): alici={}", LogMasking.phone(request.recipientContact()));
                return NotificationSendOutcome.success("Ileti Merkezi'ne iletildi");
            }
            // -1: bos/beklenmeyen yanit -> servis bozuk sayilir
            outcomes.record(IntegrationOutcomes.SMS, code > 0 && !IntegrationOutcomes.isOutageStatus(code));
            log.warn("Ileti Merkezi SMS gonderimi basarisiz: alici={}, kod={}, mesaj={}", LogMasking.phone(request.recipientContact()), code, statusMessage);
            return NotificationSendOutcome.failure("Ileti Merkezi hatasi (" + code + "): " + statusMessage);
        } catch (RestClientResponseException e) {
            outcomes.record(IntegrationOutcomes.SMS, !IntegrationOutcomes.isOutageStatus(e.getStatusCode().value()));
            log.warn(
                "Ileti Merkezi SMS gonderimi basarisiz: alici={}, durum={}, govde={}",
                LogMasking.phone(request.recipientContact()), e.getStatusCode(), LogMasking.body(e.getResponseBodyAsString())
            );
            return NotificationSendOutcome.failure("Ileti Merkezi hatasi (" + e.getStatusCode() + "): " + e.getResponseBodyAsString());
        } catch (Exception e) {
            outcomes.record(IntegrationOutcomes.SMS, false);
            log.warn("Ileti Merkezi SMS gonderimi basarisiz: alici={}, hata={}", LogMasking.phone(request.recipientContact()), e.getMessage());
            return NotificationSendOutcome.failure("Saglayiciya ulasilamadi: " + e.getMessage());
        }
```
`sendSimulated` log argümanı: `request.recipientContact()` → `LogMasking.phone(request.recipientContact())` (kayıt yok).

- [ ] **Step 5: Implement — `IletiMerkeziPlatformBillingSmsAdapter`**

Alan + iki kurucuya son parametre `IntegrationOutcomes outcomes` (yukarıdaki desen). `send` metodu:
```java
    private void send(String recipientPhone, String messageText) {
        if (!isConfigured()) {
            log.info("Platform fatura SMS'i (simule): to={}, mesaj=\"{}\"", LogMasking.phone(recipientPhone), messageText);
            return;
        }

        String requestBody = toJson(requestBody(apiKey, hash, sender, messageText, toE164(recipientPhone)));

        try {
            Map<?, ?> response = restClient.post()
                .uri(sendUrl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);
            // Ileti Merkezi is-seviyesi hatalari (kimlik, bakiye) HTTP 200 + response.status.code olarak doner.
            int code = statusCode(response);
            if (code == 200) {
                outcomes.record(IntegrationOutcomes.SMS, true);
                log.info("Platform fatura SMS'i gonderildi: to={}", LogMasking.phone(recipientPhone));
            } else {
                outcomes.record(IntegrationOutcomes.SMS, code > 0 && !IntegrationOutcomes.isOutageStatus(code));
                log.warn("Platform fatura SMS'i gonderilemedi: to={}, kod={}", LogMasking.phone(recipientPhone), code);
            }
        } catch (RestClientResponseException e) {
            outcomes.record(IntegrationOutcomes.SMS, !IntegrationOutcomes.isOutageStatus(e.getStatusCode().value()));
            log.warn("Platform fatura SMS'i gonderilemedi: to={}, durum={}, govde={}",
                LogMasking.phone(recipientPhone), e.getStatusCode(), LogMasking.body(e.getResponseBodyAsString()));
        } catch (Exception e) {
            outcomes.record(IntegrationOutcomes.SMS, false);
            log.warn("Platform fatura SMS'i gonderilemedi: to={}, hata={}", LogMasking.phone(recipientPhone), e.getMessage());
        }
    }

    private static int statusCode(Map<?, ?> response) {
        Object responseNode = response == null ? null : response.get("response");
        Object status = responseNode instanceof Map<?, ?> node ? node.get("status") : null;
        Object code = status instanceof Map<?, ?> s ? s.get("code") : null;
        return code instanceof Number n ? n.intValue() : -1;
    }
```

- [ ] **Step 6: Implement — `IyzicoPaymentGatewayAdapter`**

Tek kurucuya son parametre `IntegrationOutcomes outcomes` + alan. `initializeCheckout`:
```java
        Map<String, Object> response;
        try {
            response = postSigned(INITIALIZE_PATH, requestBody);
        } catch (RestClientResponseException e) {
            outcomes.record(IntegrationOutcomes.PAYMENT, !IntegrationOutcomes.isOutageStatus(e.getStatusCode().value()));
            log.warn("iyzico checkout baslatilamadi: durum={}, govde={}", e.getStatusCode(), LogMasking.body(e.getResponseBodyAsString()));
            throw new PaymentGatewayException("iyzico checkout baslatilamadi", e);
        } catch (RestClientException e) {
            // (mevcut yorum aynen)
            outcomes.record(IntegrationOutcomes.PAYMENT, false);
            log.warn("iyzico'ya ulasilamadi: {}", e.getMessage());
            throw new PaymentGatewayException("iyzico'ya ulasilamadi", e);
        }
        // iyzico yanit verdi: is-seviyesi "failure" (gecersiz tutar vb.) servis ayakta demek.
        outcomes.record(IntegrationOutcomes.PAYMENT, true);

        // (mevcut yorum aynen)
        if (response == null || !"success".equals(response.get("status"))) {
            log.warn("iyzico checkout basarisiz yanit dondu: govde={}", LogMasking.body(String.valueOf(response)));
            throw new PaymentGatewayException("iyzico checkout basarisiz: " + response, null);
        }
```
`retrieveCheckoutResult` try bloğu:
```java
        try {
            Map<String, Object> response = postSigned(RETRIEVE_PATH, requestBody);
            outcomes.record(IntegrationOutcomes.PAYMENT, true);
            // ... mevcut govde aynen
        } catch (RestClientResponseException e) {
            outcomes.record(IntegrationOutcomes.PAYMENT, !IntegrationOutcomes.isOutageStatus(e.getStatusCode().value()));
            log.warn("iyzico odeme sonucu sorgulanamadi: durum={}, govde={}", e.getStatusCode(), LogMasking.body(e.getResponseBodyAsString()));
            return new CheckoutResult(false, null, null);
        } catch (RestClientException e) {
            outcomes.record(IntegrationOutcomes.PAYMENT, false);
            throw e;
        }
```

- [ ] **Step 7: Modül bağımlılık izinleri**

Üç `package-info.java`'da `allowedDependencies` listesinin sonuna `"platform::observability"` ekle:
- `modules/integration/efatura/package-info.java`
- `modules/notification/package-info.java` (son öğe şu an `"platform::http"`)
- `modules/platformadmin/package-info.java`

- [ ] **Step 8: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='FaturaEntegratorEInvoiceGatewayAdapterTest,NotificationProviderAdapterTest,IletiMerkeziPlatformBillingSmsAdapterTest,IyzicoPaymentGatewayAdapterTest,ApplicationModulesTest'`
Expected: PASS (mevcut testler dahil; `should_notThrow_when_gatewayReturnsError` hâlâ geçer).

---

### Task 5: AI adaptörleri — süre ve sonuç

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/ai/infrastructure/adapter/` altındaki 6 adaptör: `OllamaSoapGenerationAdapter`, `OllamaDiagnosisSuggestionAdapter`, `OllamaTreatmentRecommendationAdapter`, `ClaudeSoapGenerationAdapter`, `ClaudeDiagnosisSuggestionAdapter`, `ClaudeTreatmentRecommendationAdapter`
- Modify: `backend/src/main/java/com/vetos/modules/ai/package-info.java`
- Test: `ClaudeSoapGenerationAdapterTest`, `ClaudeDiagnosisSuggestionAdapterTest`, `ClaudeTreatmentRecommendationAdapterTest`, `OllamaTreatmentRecommendationAdapterTest`

**Interfaces:**
- Consumes: `IntegrationOutcomes.recordAiCall(String task, String provider, Callable<T>)`.
- Produces: 6 adaptörün tüm kurucuları son parametre olarak `IntegrationOutcomes outcomes` alır.

`task`/`provider` eşlemesi:

| Sınıf | task | provider | try içindeki çağrı |
|---|---|---|---|
| `OllamaSoapGenerationAdapter` | `soap` | `ollama` | `generateViaOllama(text)` |
| `OllamaDiagnosisSuggestionAdapter` | `diagnosis` | `ollama` | `generateViaOllama(input)` |
| `OllamaTreatmentRecommendationAdapter` | `treatment` | `ollama` | `generateViaOllama(input)` |
| `ClaudeSoapGenerationAdapter` | `soap` | `claude` | `generateViaClaude(text)` |
| `ClaudeDiagnosisSuggestionAdapter` | `diagnosis` | `claude` | `generateViaClaude(input)` |
| `ClaudeTreatmentRecommendationAdapter` | `treatment` | `claude` | `generateViaClaude(input)` |

- [ ] **Step 1: Write the failing test**

`ClaudeSoapGenerationAdapterTest` — ortak yardımcı alanları (Task 4'teki `registry`, `outcomes`, `calls`) ekle; dosyadaki 5 `new ClaudeSoapGenerationAdapter(...)` çağrısına son argüman `outcomes`. Yeni testler:
```java
    @Test
    void should_recordAiDurationAndSuccess_when_claudeResponds() {
        stubResponseBody = "{\"content\":[{\"type\":\"tool_use\",\"id\":\"toolu_01\",\"name\":\"structure_soap_note\","
            + "\"input\":{\"subjective\":\"S\",\"objective\":\"O\",\"assessment\":\"A\",\"plan\":\"P\"}}]}";

        new ClaudeSoapGenerationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes)
            .generate(new AudioTranscript("Hasta kusuyor"));

        assertThat(registry.find("vetly.ai.duration").tag("task", "soap").tag("provider", "claude").timer().count()).isEqualTo(1);
        assertThat(calls("ai", "success")).isEqualTo(1);
    }

    @Test
    void should_recordAiFailure_when_claudeUnreachable() {
        stubServer.stop(0);

        new ClaudeSoapGenerationAdapter("test-api-key", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes)
            .generate(new AudioTranscript("Hasta kusuyor"));

        assertThat(calls("ai", "failure")).isEqualTo(1);
    }

    @Test
    void should_notRecord_when_apiKeyBlank() {
        new ClaudeSoapGenerationAdapter("", "claude-sonnet-5", new ObjectMapper(), stubUrl(), outcomes)
            .generate(new AudioTranscript("Hasta kusuyor"));

        assertThat(registry.find("vetly.integration.calls").counters()).isEmpty();
    }
```
Diğer üç test dosyasında (`ClaudeDiagnosisSuggestionAdapterTest` 4, `ClaudeTreatmentRecommendationAdapterTest` 4, `OllamaTreatmentRecommendationAdapterTest` 3 çağrı) sadece alanları ekle ve her `new ...Adapter(` çağrısına son argüman `outcomes` geçir.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest='Claude*AdapterTest,Ollama*AdapterTest'`
Expected: FAIL — derleme hatası.

- [ ] **Step 3: Implement**

Her adaptörde:
1. Alan: `private final IntegrationOutcomes outcomes;`
2. Kurucular: Claude sınıflarında hem `@Autowired` kurucu (`ObjectMapper objectMapper`'dan sonra `IntegrationOutcomes outcomes`; `this(...)`'a `outcomes` geçir) hem paket-özel kurucu (`String apiUrl`'den sonra `IntegrationOutcomes outcomes`; gövdede `this.outcomes = outcomes;`). Ollama sınıflarında tek kurucu: `ObjectMapper objectMapper`'dan sonra `IntegrationOutcomes outcomes`.
3. try bloğu — örnek `ClaudeSoapGenerationAdapter`:
```java
        try {
            return outcomes.recordAiCall("soap", "claude", () -> generateViaClaude(text));
        } catch (Exception e) {
```
Diğer beş sınıfta yukarıdaki tabloya göre aynı satır (`text`/`input` değişkeni ve metot adı tablodaki gibi). `catch` bloğu ve fallback aynen kalır.
4. Import: `com.vetos.platform.observability.IntegrationOutcomes`.

`modules/ai/package-info.java` `allowedDependencies` sonuna `"platform::observability"`.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='Claude*AdapterTest,Ollama*AdapterTest,ApplicationModulesTest'`
Expected: PASS.

---

### Task 6: Kuyruk metrikleri (gauge)

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/notification/domain/NotificationLogRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/infrastructure/persistence/NotificationLogJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/infrastructure/persistence/NotificationLogRepositoryAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/notification/infrastructure/metrics/NotificationQueueMetrics.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmissionRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionRepositoryAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/metrics/EInvoiceQueueMetrics.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSubmissionRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/persistence/TarbilSubmissionJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/persistence/TarbilSubmissionRepositoryAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/metrics/TarbilQueueMetrics.java`
- Test: `backend/src/test/java/com/vetos/modules/notification/infrastructure/metrics/NotificationQueueMetricsTest.java`
- Test: `backend/src/test/java/com/vetos/modules/integration/efatura/infrastructure/metrics/EInvoiceQueueMetricsTest.java`
- Test: `backend/src/test/java/com/vetos/modules/integration/tarbil/infrastructure/metrics/TarbilQueueMetricsTest.java`

**Interfaces:**
- Consumes: `MeterRegistry` (Spring bean).
- Produces: `long countByStatus(NotificationStatus)`, `long countByStatus(EInvoiceSubmissionStatus)`, `long countByStatus(TarbilSyncStatus)` domain repository metotları; gauge'lar `vetly.queue.pending{queue=notification|efatura|tarbil}`, `vetly.queue.failed{queue=notification|efatura}`.

Sayım kuralları: notification pending = `PENDING`, failed = `FAILED`; efatura pending = `PENDING` + `PROCESSING`, failed = `FAILED`; tarbil pending = `PENDING` (failed yok — eklenti hata bildirmiyor).
Not: gauge okuması TenantContext'siz (scrape isteği) çalışır → Hibernate root session, tüm kiracılar sayılır (`TenantContextIdentifierResolver`). Bu istenen davranış.

- [ ] **Step 1: Write the failing tests**

`NotificationQueueMetricsTest.java`:
```java
package com.vetos.modules.notification.infrastructure.metrics;

import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NotificationQueueMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final NotificationLogRepository repository = mock(NotificationLogRepository.class);

    @Test
    void should_reportPendingAndFailedCounts() {
        when(repository.countByStatus(NotificationStatus.PENDING)).thenReturn(4L);
        when(repository.countByStatus(NotificationStatus.FAILED)).thenReturn(2L);
        new NotificationQueueMetrics(registry, repository);

        assertThat(registry.get("vetly.queue.pending").tag("queue", "notification").gauge().value()).isEqualTo(4.0);
        assertThat(registry.get("vetly.queue.failed").tag("queue", "notification").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void should_reportNaN_when_databaseFails() {
        when(repository.countByStatus(NotificationStatus.PENDING)).thenThrow(new IllegalStateException("db yok"));
        new NotificationQueueMetrics(registry, repository);

        assertThat(registry.get("vetly.queue.pending").tag("queue", "notification").gauge().value()).isNaN();
    }
}
```

`EInvoiceQueueMetricsTest.java`:
```java
package com.vetos.modules.integration.efatura.infrastructure.metrics;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EInvoiceQueueMetricsTest {

    @Test
    void should_countPendingAndProcessingAsPending() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        EInvoiceSubmissionRepository repository = mock(EInvoiceSubmissionRepository.class);
        when(repository.countByStatus(EInvoiceSubmissionStatus.PENDING)).thenReturn(3L);
        when(repository.countByStatus(EInvoiceSubmissionStatus.PROCESSING)).thenReturn(5L);
        when(repository.countByStatus(EInvoiceSubmissionStatus.FAILED)).thenReturn(1L);
        new EInvoiceQueueMetrics(registry, repository);

        assertThat(registry.get("vetly.queue.pending").tag("queue", "efatura").gauge().value()).isEqualTo(8.0);
        assertThat(registry.get("vetly.queue.failed").tag("queue", "efatura").gauge().value()).isEqualTo(1.0);
    }
}
```

`TarbilQueueMetricsTest.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.metrics;

import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TarbilQueueMetricsTest {

    @Test
    void should_reportPendingSubmissions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        TarbilSubmissionRepository repository = mock(TarbilSubmissionRepository.class);
        when(repository.countByStatus(TarbilSyncStatus.PENDING)).thenReturn(7L);
        new TarbilQueueMetrics(registry, repository);

        assertThat(registry.get("vetly.queue.pending").tag("queue", "tarbil").gauge().value()).isEqualTo(7.0);
        assertThat(registry.find("vetly.queue.failed").tag("queue", "tarbil").gauge()).isNull();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend && ./mvnw -q test -Dtest='NotificationQueueMetricsTest,EInvoiceQueueMetricsTest,TarbilQueueMetricsTest'`
Expected: FAIL — derleme hatası (`countByStatus`, metrik sınıfları yok).

- [ ] **Step 3: Implement repository methods**

Her domain repository arayüzüne (tipine göre):
```java
    /** Kuyruk metrigi (vetly.queue.*) icin; TenantContext'siz cagrilir -> tum kiracilar. */
    long countByStatus(NotificationStatus status);
```
(`EInvoiceSubmissionRepository` → `EInvoiceSubmissionStatus`, `TarbilSubmissionRepository` → `TarbilSyncStatus`.)

Her JPA arayüzüne (Spring Data türetilmiş sorgu):
```java
    long countByStatus(NotificationStatus status);
```
(tip aynı şekilde; `NotificationLogJpaRepository` diğer metotlarında tam nitelikli ad kullanıyorsa aynı stili izle.)

Her repository adaptörüne:
```java
    @Override
    public long countByStatus(NotificationStatus status) {
        return jpaRepository.countByStatus(status);
    }
```
(`TarbilSubmissionRepositoryAdapter`'daki JPA alanının adı farklıysa o adı kullan.)

- [ ] **Step 4: Implement metric components**

`NotificationQueueMetrics.java`:
```java
package com.vetos.modules.notification.infrastructure.metrics;

import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Bildirim kuyrugu derinligi (spec 2026-10-14 S4.3). Deger scrape aninda DB'den sayilir;
 * sorgu hata verirse Micrometer gauge'u NaN raporlar, scrape kirilmaz.
 */
@Component
class NotificationQueueMetrics {

    NotificationQueueMetrics(MeterRegistry registry, NotificationLogRepository repository) {
        Gauge.builder("vetly.queue.pending", repository, r -> r.countByStatus(NotificationStatus.PENDING))
            .tag("queue", "notification")
            .register(registry);
        Gauge.builder("vetly.queue.failed", repository, r -> r.countByStatus(NotificationStatus.FAILED))
            .tag("queue", "notification")
            .register(registry);
    }
}
```

`EInvoiceQueueMetrics.java`:
```java
package com.vetos.modules.integration.efatura.infrastructure.metrics;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/** E-fatura kuyrugu: PROCESSING (GIB resmilesmesi bekleyen) da "bekleyen" sayilir. */
@Component
class EInvoiceQueueMetrics {

    EInvoiceQueueMetrics(MeterRegistry registry, EInvoiceSubmissionRepository repository) {
        Gauge.builder("vetly.queue.pending", repository,
                r -> r.countByStatus(EInvoiceSubmissionStatus.PENDING) + r.countByStatus(EInvoiceSubmissionStatus.PROCESSING))
            .tag("queue", "efatura")
            .register(registry);
        Gauge.builder("vetly.queue.failed", repository, r -> r.countByStatus(EInvoiceSubmissionStatus.FAILED))
            .tag("queue", "efatura")
            .register(registry);
    }
}
```

`TarbilQueueMetrics.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.metrics;

import com.vetos.modules.integration.tarbil.domain.TarbilSubmissionRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Hekim onayi bekleyen TARBIL gonderimleri. "failed" yok: eklenti hata bildirmiyor,
 * kayit ya SUBMITTED ya DISMISSED olur.
 */
@Component
class TarbilQueueMetrics {

    TarbilQueueMetrics(MeterRegistry registry, TarbilSubmissionRepository repository) {
        Gauge.builder("vetly.queue.pending", repository, r -> r.countByStatus(TarbilSyncStatus.PENDING))
            .tag("queue", "tarbil")
            .register(registry);
    }
}
```

`modules/integration/tarbil/package-info.java`'ya bu görevde izin gerekmez (Micrometer harici kütüphane; `platform::observability` kullanılmıyor).

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='NotificationQueueMetricsTest,EInvoiceQueueMetricsTest,TarbilQueueMetricsTest,ApplicationModulesTest'`
Expected: PASS.

---

### Task 7: Kalan PII log satırları ve koruma testi

**Files:**
- Test: `backend/src/test/java/com/vetos/platform/observability/LogPiiGuardTest.java`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/application/StartImpersonationUseCase.java` (log ~satır 44-47)
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/MockPlatformAnnouncementEmailAdapter.java:13`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/MockPlatformBillingEmailAdapter.java:21-37`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/adapter/MockInviteEmailAdapter.java:22-25`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/package-info.java`
- Modify: guard testinin raporladığı diğer satırlar

**Interfaces:**
- Consumes: `LogMasking.phone/email/body`.
- Produces: `LogPiiGuardTest.violations(String source, String fileName): List<String>` (paket-özel, statik).

- [ ] **Step 1: Write the failing test**

```java
package com.vetos.platform.observability;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kaynak taramasi (spec 2026-10-14 S4.5): log cagrilarina telefon/e-posta/yanit govdesi
 * LogMasking'den gecmeden verilirse kirilir. Hata mesaji dosya:satir listesi verir.
 */
class LogPiiGuardTest {

    private static final Pattern LOG_CALL_START = Pattern.compile("\\blog\\.(trace|debug|info|warn|error)\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"");
    private static final Pattern MASK_CALL = Pattern.compile("LogMasking\\.\\w+\\(");
    private static final Pattern SENSITIVE = Pattern.compile(
        "[\\w.]*?(?:getResponseBodyAsString\\(\\)|recipientContact|\\w*(?:[pP]hone|[eE]mail)\\w*)");
    private static final Pattern MASKED_PREFIX = Pattern.compile("LogMasking\\.M\\(\\s*$");

    static List<String> violations(String source, String fileName) {
        List<String> found = new ArrayList<>();
        Matcher start = LOG_CALL_START.matcher(source);
        while (start.find()) {
            int end = callEnd(source, start.end() - 1);
            String args = STRING_LITERAL.matcher(source.substring(start.end(), end)).replaceAll("\"\"");
            args = MASK_CALL.matcher(args).replaceAll("LogMasking.M(");
            Matcher m = SENSITIVE.matcher(args);
            while (m.find()) {
                if (!MASKED_PREFIX.matcher(args.substring(0, m.start())).find()) {
                    found.add(fileName + ":" + lineOf(source, start.start()) + " -> " + m.group());
                }
            }
        }
        return found;
    }

    private static int callEnd(String s, int openParen) {
        int depth = 0;
        char quote = 0;
        for (int i = openParen; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return i;
            }
        }
        return s.length();
    }

    private static int lineOf(String s, int index) {
        int line = 1;
        for (int i = 0; i < index; i++) {
            if (s.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    @Test
    void should_flagUnmaskedSensitiveArguments() {
        assertThat(violations("log.warn(\"x={}\", request.recipientContact());", "A.java")).hasSize(1);
        assertThat(violations("log.info(\n  \"to={}\",\n  ownerEmail\n);", "B.java")).containsExactly("B.java:1 -> ownerEmail");
        assertThat(violations("log.warn(\"g={}\", e.getResponseBodyAsString());", "C.java")).hasSize(1);
        assertThat(violations("log.info(\"a={}, b={}\", LogMasking.phone(p), recipientPhone);", "D.java")).hasSize(1);
    }

    @Test
    void should_acceptMaskedArgumentsAndPlainText() {
        assertThat(violations("log.warn(\"x={}\", LogMasking.phone(request.recipientContact()));", "A.java")).isEmpty();
        assertThat(violations("log.info(\"to={}\", LogMasking.email(invite.getEmail()));", "B.java")).isEmpty();
        assertThat(violations("log.warn(\"g={}\", LogMasking.body(e.getResponseBodyAsString()));", "C.java")).isEmpty();
        assertThat(violations("log.info(\"email gonderildi, phone yok\");", "D.java")).isEmpty();
    }

    @Test
    void should_haveNoUnmaskedPersonalDataInLogs() throws IOException {
        List<String> all = new ArrayList<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                all.addAll(violations(Files.readString(file), file.toString().replace('\\', '/')));
            }
        }
        assertThat(all).as("Maskelenmemis kisisel veri iceren log satirlari (LogMasking kullanin)").isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend && ./mvnw -q test -Dtest=LogPiiGuardTest`
Expected: İlk iki test PASS; `should_haveNoUnmaskedPersonalDataInLogs` FAIL — en az şu satırları listeler: `StartImpersonationUseCase` (`platformAdminEmail`), `MockPlatformAnnouncementEmailAdapter` (`recipientEmail`), `MockPlatformBillingEmailAdapter` (3 × `recipientEmail`), `MockInviteEmailAdapter` (`invite.getEmail`). (Task 4'teki satırlar zaten maskeli.)

- [ ] **Step 3: Fix the reported lines**

- `StartImpersonationUseCase`: log argümanı `platformAdminEmail` → `LogMasking.email(platformAdminEmail)`.
- `MockPlatformAnnouncementEmailAdapter:13`: `recipientEmail` → `LogMasking.email(recipientEmail)`.
- `MockPlatformBillingEmailAdapter`: üç log çağrısında `recipientEmail` → `LogMasking.email(recipientEmail)`.
- `MockInviteEmailAdapter`: `invite.getEmail()` → `LogMasking.email(invite.getEmail())`.
- Her dosyaya import `com.vetos.platform.observability.LogMasking`.
- `modules/tenant/package-info.java` `allowedDependencies` sonuna `"platform::observability"` (platformadmin Task 4'te eklendi).
- Test başka bir satır daha raporlarsa: değer gerçekten kişisel veriyse (telefon/e-posta/yanıt gövdesi) uygun `LogMasking` yardımcısıyla sar ve modülün `package-info.java`'sına izni ekle. Kişisel veri değilse (ör. adında `phone` geçen bir yapılandırma kimliği `metaPhoneNumberId`), değeri loglamaktan vazgeç veya `LogMasking.body(...)` ile sar — istisna listesi EKLEME.

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend && ./mvnw -q test -Dtest='LogPiiGuardTest,ApplicationModulesTest'`
Expected: PASS (3/3 + modül testi).

---

### Task 8: Belgeler, tam test takımı ve devreye alma notu

**Files:**
- Modify: `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` (Faz 3 bölümü)
- Modify: `docs/superpowers/specs/2026-10-14-gozlemlenebilirlik-faz3-design.md` (sadece Step 3'teki sapma varsa)

- [ ] **Step 1: Yol haritasını güncelle**

`## Faz 3 — Gözlemlenebilirlik ve loglama  ← **SIRADAKİ**` başlığını şuna çevir:
```markdown
## Faz 3 — Gözlemlenebilirlik ve loglama  ✅ kod tamamlandı (2026-10-14) — devreye alma: spec §7 (`docs/superpowers/specs/2026-10-14-gozlemlenebilirlik-faz3-design.md`)
```
Bölümün altındaki maddelerden 3.6 ve 3.7'yi şu iki satırla değiştir:
```markdown
- **3.6 Audit log:** → **Faz 3b** (ayrı tasarım).
- **3.7 Tracing:** kapsam dışı bırakıldı (tek uygulama; Faz 1 requestId yeterli).
```
Faz 4 başlığının sonuna ` ← **SIRADAKİ**` ekle; Faz 4'ten önce yeni bir başlık ekle:
```markdown
## Faz 3b — Audit log
- `platform/audit` (boş paket): login, rol değişikliği, fatura iptal, reçete, TARBİL gönderimi için `audit_events` tablosu + `AuditPort`. Ayrı tasarım turu.
```
(Not: Faz 3b, Faz 4'ten önce mi sonra mı yapılacağı kullanıcıya sorulacak; `SIRADAKİ` işaretini Faz 4'e koy, kullanıcı aksini söylerse taşı.)

- [ ] **Step 2: Tam backend test takımını çalıştır**

Run: `cd backend && ./mvnw -q test` (arka planda; ~3-5 dk)
Expected: BUILD SUCCESS, 0 failure. Toplam test sayısı Faz 2 sonundaki 598'den fazla olmalı.

Kırmızı test varsa: önce yerelde :8080 backend'in eski şemayla çalışıp çalışmadığını değil (bu fazda migration yok), `ApplicationModulesTest` ve kurucu değişikliğinden etkilenen başka bir test sınıfı olup olmadığını kontrol et: `grep -rln "new \(NotificationProviderAdapter\|IletiMerkeziPlatformBillingSmsAdapter\|IyzicoPaymentGatewayAdapter\|FaturaEntegratorEInvoiceGatewayAdapter\|Ollama\w*Adapter\|Claude\w*Adapter\)(" src/test/java` — listelenen her dosyada `outcomes` argümanı olmalı.

- [ ] **Step 3: Sapma kontrolü**

Task 3 Step 4'teki geri dönüş yolu (profil) kullanıldıysa spec §4.4 ve §7 adım 6'yı buna göre güncelle. Kullanılmadıysa bu adımı atla.

- [ ] **Step 4: Devreye alma notlarını doğrula**

Spec §7'deki nginx bloğunun `location = /health/ready` ve `location = /health/external` içerdiğini ve UptimeRobot için şu notun eklendiğini kontrol et; yoksa §7 adım 5'in sonuna ekle:
```markdown
   Monitör tipi **HTTP(s)**, metod **GET** (HEAD değil — Actuator health uçları için GET kullan).
```
