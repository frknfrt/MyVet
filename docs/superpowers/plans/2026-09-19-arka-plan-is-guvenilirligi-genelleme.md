# Arka Plan İş Güvenilirliği Genelleme Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** `notification` modülünde henüz hiç implemente edilmemiş retry+backoff/advisory-lock desenini kurmak, sonra bu deseni `tarbil` ve `efatura` modüllerine ve `PlatformBillingScheduler`'a genelleştirmek — böylece dört arka plan işinin (bildirim gönderimi, randevu hatırlatma, TARBİL senkronu, e-Fatura gönderimi, platform faturalama) hepsi çoklu-instance'a güvenli, otomatik tekrar denemeli ve kiracı kontrollü olur.

**Architecture:** `notification_log`/`tarbil_sync_log`/`efatura_submission` tablolarına `attempt_count`/`next_retry_at` eklenir; `FOR UPDATE SKIP LOCKED` ile satır bazlı claim yapan bir zamanlanmış süpürme işi (2 dakikada bir) başarısız kayıtları otomatik tekrar dener (üstel backoff, en fazla 4 otomatik deneme). `platform/concurrency/AdvisoryLock` (Postgres transaction-scoped advisory lock) tekil-üretim işlerini (randevu hatırlatma, platform faturalama) çoklu instance'ta çakışmaya karşı korur. Sıkışan `PROCESSING` e-Fatura kayıtları için mevcut `ApplyEInvoiceCallbackUseCase` saatlik bir zamanlayıcıdan da tetiklenir (yeni çözümleme mantığı yazılmadan). Manuel "Tekrar Dene" uç noktaları, Kiracı İzolasyonu turunda `NotificationLog` için kurulan aynı elle-kiracı-kontrolü desenini alır.

**Tech Stack:** Spring `@Async`/`@Scheduled`, Postgres `FOR UPDATE SKIP LOCKED` + `pg_try_advisory_xact_lock`, Flyway, JUnit 5 + Mockito.

**Spec:**
- `docs/superpowers/specs/2026-09-17-bildirim-teslim-sertlestirme-design.md` (temel desen — bu plan yazılana kadar implemente edilmemişti, bu planla birlikte kuruluyor)
- `docs/superpowers/specs/2026-09-19-arka-plan-is-guvenilirligi-genelleme-design.md` (aynı desenin `tarbil`/`efatura`/`PlatformBillingScheduler`'a genelleştirilmesi)

## Global Constraints

- Backoff sabitleri üç modülde de **aynı**: `{2dk, 10dk, 1sa, 6sa}`, en fazla 4 otomatik retry (5. denemede `nextRetryAt=null`, sadece manuel "Tekrar Dene" kalır).
- Süpürme (sweep) işleri `FOR UPDATE SKIP LOCKED` ile satır claim eder — ayrı bir advisory lock'a ihtiyaç duymazlar (çakışma satır seviyesinde çözülür). Advisory lock sadece **iş üretimi** yapan işlerde (`AppointmentReminderScheduler`, `PlatformBillingScheduler`) kullanılır.
- `AdvisoryLock.tryAcquire(key)` transaction-scoped'tur — çağıran metot `@Transactional` olmalı, kilit o transaction'ın ömrüne bağlıdır.
- **`efatura_submission` claim sorgusu `status = 'FAILED'` filtresini asla gevşetmez** — `PROCESSING` durumundaki bir kayıt otomatik süpürmeye asla girmez (mükerrer GIB gönderimi riski). Bu, bu planın en kritik davranış garantisidir ve ayrı bir testle doğrulanır.
- `TarbilSyncLog` ve `NotificationLog`, Kiracı İzolasyonu turunda bilinçli olarak `@TenantId` dışında tutuldu (arka plan/`@Async` kodundan filtresiz erişilebilmeleri gerekiyor) — bu planda da o karar korunur: yeni `tarbil_sync_log.tenant_id` kolonu düz bir sütun olarak eklenir, Hibernate `@TenantId` **eklenmez**; manuel retry endpoint'lerinde elle (`'.filter(...)'`) kontrol edilir, `NotificationLog`/`RetryNotificationUseCase` ile birebir aynı desen.
- `EInvoiceSubmission` zaten `tenant_id`'ye sahip (Kiracı İzolasyonu kapsamı dışında bırakılmıştı çünkü zaten vardı) — yeni migration gerekmiyor, sadece retry alanları ekleniyor.
- Bu kod tabanında pure repository/adaptör (native `@Query`) katmanı için ayrı test yazılmaz (mevcut proje kuralı) — doğrulama use-case testleriyle (mock repository) yapılır.
- Yeni `platform/concurrency` paketi, `platform/tenancy` ile aynı desende `@NamedInterface("concurrency")` alır (modül sınırları dışından enjekte edilebilmesi için).
- Her görev sonunda `cd backend && ./mvnw test` çalıştırılır ve `ApplicationModulesTest` dahil tüm paket yeşil olmalı.
- Migration numaraları: bir önceki turun (Kiracı İzolasyonu) son migration'ı `V51`'dir — bu plan `V52`'den başlar.

---

## Task 1: Paylaşımlı Thread Havuzu + Advisory Lock + Randevu Hatırlatma Kilidi

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/config/AsyncConfig.java`
- Create: `backend/src/main/java/com/vetos/platform/concurrency/AdvisoryLock.java`
- Create: `backend/src/main/java/com/vetos/platform/concurrency/package-info.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderScheduler.java`
- Modify: `backend/src/test/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderSchedulerTest.java`

**Interfaces:**
- Produces: `AdvisoryLock.tryAcquire(long key): boolean` — Task 11 (`PlatformBillingScheduler`) bunu aynı şekilde kullanacak.

- [ ] **Step 1: `AsyncConfig` oluştur**

`backend/src/main/java/com/vetos/platform/config/AsyncConfig.java`:
```java
package com.vetos.platform.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * @Async icin paylasilan, sinirli boyutlu executor -- notification, tarbil
 * ve e-fatura outbox executor'lari (NotificationSendExecutor,
 * TarbilSyncExecutor, EInvoiceSubmissionExecutor) hicbir qualifier
 * belirtmedigi icin hepsi bu bean'i kullanir. Kuyruk dolarsa cagiran
 * thread'in kendisi gorevi calistirir (CallerRunsPolicy) -- is kaybolmaz,
 * bunun yerine cagiran istek dogal olarak yavaslar (geri basinc).
 */
@Configuration
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("async-outbox-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

- [ ] **Step 2: `platform/concurrency` paketi + `AdvisoryLock` oluştur**

`backend/src/main/java/com/vetos/platform/concurrency/package-info.java`:
```java
@org.springframework.modulith.NamedInterface("concurrency")
package com.vetos.platform.concurrency;
```

`backend/src/main/java/com/vetos/platform/concurrency/AdvisoryLock.java`:
```java
package com.vetos.platform.concurrency;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Postgres transaction-scoped advisory lock -- mevcut transaction commit/
 * rollback olunca otomatik serbest kalir (oturum/connection havuzunda sizinti
 * riski yok). Coklu instance'ta ayni @Scheduled isin sadece BIR instance
 * tarafindan calistirilmasini garanti etmek icin kullanilir. Cagiran metot
 * @Transactional olmalidir -- kilit o transaction'in omrune baglidir.
 */
@Component
@RequiredArgsConstructor
public class AdvisoryLock {
    private final EntityManager entityManager;

    /** true donerse kilit alindi, is yapilabilir. false donerse baska bir instance zaten calisiyor. */
    public boolean tryAcquire(long key) {
        Object result = entityManager.createNativeQuery("SELECT pg_try_advisory_xact_lock(:key)")
            .setParameter("key", key)
            .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
```

- [ ] **Step 3: `AppointmentReminderScheduler`'ı advisory lock ile sarmala**

`backend/src/main/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderScheduler.java` — importlara ekle:
```java
import com.vetos.platform.concurrency.AdvisoryLock;
import org.springframework.transaction.annotation.Transactional;
```
Alan ekle (constructor `@RequiredArgsConstructor` ile otomatik oluşur, sadece alanı ekle):
```java
private static final long REMINDER_LOCK_KEY = 7_301_001; // sabit, bu is icin ayrilmis rastgele bir anahtar

private final AdvisoryLock advisoryLock;
```
`sendTomorrowReminders()` metodunu değiştir:
```java
@Scheduled(cron = "0 0 9 * * *", zone = "Europe/Istanbul")
@Transactional
public void sendTomorrowReminders() {
    if (!advisoryLock.tryAcquire(REMINDER_LOCK_KEY)) {
        log.info("Randevu hatirlatma kilidi baska bir instance'da -- atlaniyor");
        return;
    }

    Instant tomorrowStart = LocalDate.now(ISTANBUL).plusDays(1).atStartOfDay(ISTANBUL).toInstant();
    Instant tomorrowEnd = tomorrowStart.plus(1, ChronoUnit.DAYS);

    for (UUID tenantId : tenantLookupPort.findActiveTenantIds()) {
        // Koprulme kurali: bu is bir HTTP istegi icinde calismiyor, ama
        // tenantId elimizde. @TenantId'li entity'lere (Patient, Encounter...)
        // dokunmadan once TenantContext kurulmali -- aksi halde sorgu root
        // Session'da, yani FILTRESIZ calisir.
        TenantContext.set(tenantId);
        try {
            int queued = sendAppointmentRemindersUseCase.execute(tenantId, tomorrowStart, tomorrowEnd);
            if (queued > 0) {
                log.info("Randevu hatirlatmasi kuyruklandi: tenantId={}, adet={}", tenantId, queued);
            }
        } catch (Exception e) {
            log.error("Randevu hatirlatma isi basarisiz: tenantId={}", tenantId, e);
        } finally {
            TenantContext.clear();
        }
    }
}
```
(Metot gövdesinin geri kalanı — tenant döngüsü, köprüleme, try/catch/finally — değişmiyor; sadece başa kilit kontrolü ve `@Transactional` ekleniyor.)

- [ ] **Step 4: Mevcut testi yeni constructor parametresine güncelle + yeni "kilit alınamazsa atla" testi ekle**

`backend/src/test/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderSchedulerTest.java` — importlara ekle:
```java
import com.vetos.platform.concurrency.AdvisoryLock;
```
Mock alanı ekle:
```java
@Mock private AdvisoryLock advisoryLock;
```
Mevcut iki testte (`should_setTenantContext_forEachTenant_and_clearAfterwards`, `should_clearTenantContext_evenWhenUseCaseThrows`) constructor çağrısını güncelle ve kilidin alındığını stub'la:
```java
AppointmentReminderScheduler scheduler =
    new AppointmentReminderScheduler(tenantLookupPort, sendAppointmentRemindersUseCase, advisoryLock);
```
Her iki testin başına (ilk `when(...)` çağrısından önce) ekle:
```java
when(advisoryLock.tryAcquire(anyLong())).thenReturn(true);
```
(`anyLong` importu: `import static org.mockito.ArgumentMatchers.anyLong;`)

Yeni test ekle:
```java
@Test
void should_skipEntirely_when_lockNotAcquired() {
    AppointmentReminderScheduler scheduler =
        new AppointmentReminderScheduler(tenantLookupPort, sendAppointmentRemindersUseCase, advisoryLock);
    when(advisoryLock.tryAcquire(anyLong())).thenReturn(false);

    scheduler.sendTomorrowReminders();

    verifyNoInteractions(tenantLookupPort, sendAppointmentRemindersUseCase);
}
```
(`verifyNoInteractions` importu: `import static org.mockito.Mockito.verifyNoInteractions;`)

- [ ] **Step 5: Derle ve testleri çalıştır**

Run: `cd backend && ./mvnw -q test`
Expected: tüm testler PASS, `ApplicationModulesTest` dahil (yeni `platform/config` ve `platform/concurrency` paketleri modül sınırlarını bozmamalı).

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/vetos/platform/config/AsyncConfig.java \
  backend/src/main/java/com/vetos/platform/concurrency/AdvisoryLock.java \
  backend/src/main/java/com/vetos/platform/concurrency/package-info.java \
  backend/src/main/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderScheduler.java \
  backend/src/test/java/com/vetos/modules/notification/infrastructure/scheduling/AppointmentReminderSchedulerTest.java
git commit -m "feat: paylasimli thread havuzu + advisory lock, randevu hatirlatmasina uygula"
```

---

## Task 2: `NotificationLog` — Retry Alanları

**Files:**
- Create: `backend/src/main/resources/db/migration/V52__notification_log_retry.sql`
- Modify: `backend/src/main/java/com/vetos/modules/notification/domain/NotificationLog.java`
- Create: `backend/src/test/java/com/vetos/modules/notification/domain/NotificationLogTest.java`

**Interfaces:**
- Produces: `NotificationLog.markFailed(Instant nextRetryAt)` (eski `markFailed()` imzasının yerine geçer), `getAttemptCount(): int`, `getNextRetryAt(): Instant` — Task 3 bunları kullanır.

- [ ] **Step 1: Migration oluştur**

`backend/src/main/resources/db/migration/V52__notification_log_retry.sql`:
```sql
ALTER TABLE notification_log ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE notification_log ADD COLUMN next_retry_at TIMESTAMPTZ;
CREATE INDEX idx_notification_log_due_retry ON notification_log (next_retry_at)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;
```

- [ ] **Step 2: Domain testini yaz (önce başarısız olacak şekilde)**

`backend/src/test/java/com/vetos/modules/notification/domain/NotificationLogTest.java`:
```java
package com.vetos.modules.notification.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationLogTest {

    private NotificationLog aLog() {
        return NotificationLog.queue(
            UUID.randomUUID(), UUID.randomUUID(), null, NotificationChannel.SMS, NotificationType.APPOINTMENT_REMINDER,
            "05551234567", "mesaj", null, null
        );
    }

    @Test
    void should_incrementAttemptCount_and_setNextRetryAt_when_markFailed() {
        NotificationLog log = aLog();
        Instant nextRetry = Instant.now().plusSeconds(120);

        log.markFailed(nextRetry);

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(log.getAttemptCount()).isEqualTo(1);
        assertThat(log.getNextRetryAt()).isEqualTo(nextRetry);
    }

    @Test
    void should_accumulateAttemptCount_across_multipleFailures() {
        NotificationLog log = aLog();

        log.markFailed(Instant.now().plusSeconds(120));
        log.markFailed(Instant.now().plusSeconds(600));

        assertThat(log.getAttemptCount()).isEqualTo(2);
    }

    @Test
    void should_allowNullNextRetryAt_when_automaticRetriesExhausted() {
        NotificationLog log = aLog();

        log.markFailed(null);

        assertThat(log.getNextRetryAt()).isNull();
        assertThat(log.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void should_clearNextRetryAt_when_markRetrying() {
        NotificationLog log = aLog();
        log.markFailed(Instant.now().plusSeconds(120));

        log.markRetrying();

        assertThat(log.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(log.getNextRetryAt()).isNull();
    }
}
```

- [ ] **Step 3: Testi çalıştır, derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=NotificationLogTest`
Expected: derleme hatası — `markFailed(Instant)` henüz yok, `getAttemptCount()`/`getNextRetryAt()` henüz yok.

- [ ] **Step 4: `NotificationLog` entity'sini güncelle**

`backend/src/main/java/com/vetos/modules/notification/domain/NotificationLog.java` — `attemptedAt` alanından hemen sonra ekle:
```java
@Column(name = "attempt_count", nullable = false)
private int attemptCount;

@Column(name = "next_retry_at")
private Instant nextRetryAt;
```
`markFailed()` metodunu değiştir:
```java
// backoff karari CAGIRANA (NotificationSendExecutor) birakilir, entity kendi
// backoff politikasini bilmez (bkz. reference-module.md ince entity deseni)
public void markFailed(Instant nextRetryAt) {
    this.status = NotificationStatus.FAILED;
    this.attemptedAt = Instant.now();
    this.attemptCount++;
    this.nextRetryAt = nextRetryAt; // null = otomatik retry hakki bitti, sadece manuel
}
```
`markRetrying()` metodunu değiştir (next_retry_at'i temizle):
```java
public void markRetrying() {
    this.status = NotificationStatus.PENDING;
    this.attemptedAt = Instant.now();
    this.nextRetryAt = null;
}
```

- [ ] **Step 5: Testi tekrar çalıştır, geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=NotificationLogTest`
Expected: 4 test PASS.

- [ ] **Step 6: `NotificationSendExecutor`'daki eski `markFailed()` çağrısını geçici olarak düzelt (derleme kırılmasın diye)**

`backend/src/main/java/com/vetos/modules/notification/application/NotificationSendExecutor.java` içindeki `notificationLog.markFailed();` satırını `notificationLog.markFailed(null);` yap (Task 3'te gerçek backoff hesaplamasıyla değiştirilecek — bu adım sadece derlemeyi yeşile döndürür).

- [ ] **Step 7: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V52__notification_log_retry.sql \
  backend/src/main/java/com/vetos/modules/notification/domain/NotificationLog.java \
  backend/src/main/java/com/vetos/modules/notification/application/NotificationSendExecutor.java \
  backend/src/test/java/com/vetos/modules/notification/domain/NotificationLogTest.java
git commit -m "feat: NotificationLog'a attempt_count/next_retry_at ekle"
```

---

## Task 3: Bildirim Otomatik Retry Süpürmesi

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/notification/domain/NotificationLogRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/infrastructure/persistence/NotificationLogJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/infrastructure/persistence/NotificationLogRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/notification/application/NotificationSendExecutor.java`
- Create: `backend/src/main/java/com/vetos/modules/notification/application/RetryDueNotificationsUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/notification/infrastructure/scheduling/NotificationRetryScheduler.java`
- Test: `backend/src/test/java/com/vetos/modules/notification/application/NotificationSendExecutorBackoffTest.java`
- Test: `backend/src/test/java/com/vetos/modules/notification/application/RetryDueNotificationsUseCaseTest.java`

**Interfaces:**
- Consumes: `NotificationLog.markFailed(Instant)` (Task 2).
- Produces: `NotificationLogRepository.claimDueForRetry(Instant now, int limit): List<NotificationLog>` — Tarbil/e-Fatura görevleri aynı adı kendi repository'lerinde tekrarlayacak (farklı arayüz, aynı isim/imza deseni).

- [ ] **Step 1: Port'a `claimDueForRetry` ekle**

`backend/src/main/java/com/vetos/modules/notification/domain/NotificationLogRepository.java`:
```java
package com.vetos.modules.notification.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository {
    NotificationLog save(NotificationLog log);
    Optional<NotificationLog> findById(UUID id);
    List<NotificationLog> findByTenantId(UUID tenantId);
    boolean existsByRelatedEntityIdAndNotificationType(UUID relatedEntityId, NotificationType notificationType);
    List<NotificationLog> claimDueForRetry(Instant now, int limit);
}
```

- [ ] **Step 2: JPA repository'ye native sorgu ekle**

`backend/src/main/java/com/vetos/modules/notification/infrastructure/persistence/NotificationLogJpaRepository.java` içine ekle (dosyanın mevcut import/interface yapısını koru, sadece yeni metodu ekle):
```java
@org.springframework.data.jpa.repository.Query(value = """
    SELECT * FROM notification_log
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL AND next_retry_at <= :now
    ORDER BY next_retry_at
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
List<NotificationLog> claimDueForRetry(
    @org.springframework.data.repository.query.Param("now") java.time.Instant now,
    @org.springframework.data.repository.query.Param("limit") int limit
);
```
(Var olan importlar dosyada zaten `Query`/`Param`'ı barındırmıyorsa yukarıdaki gibi tam nitelikli isimle ekle, ya da dosyanın tepesine `import org.springframework.data.jpa.repository.Query;` ve `import org.springframework.data.repository.query.Param;` ekleyip kısa isimle kullan — dosyadaki mevcut import stiline uy.)

- [ ] **Step 3: Adaptöre delege eden metodu ekle**

`backend/src/main/java/com/vetos/modules/notification/infrastructure/persistence/NotificationLogRepositoryAdapter.java` içine ekle:
```java
@Override
public List<NotificationLog> claimDueForRetry(Instant now, int limit) {
    return jpaRepository.claimDueForRetry(now, limit);
}
```
(Gerekirse dosyanın tepesine `import java.time.Instant;` ekle.)

- [ ] **Step 4: `NotificationSendExecutor`'a backoff hesaplamasını ekle**

`backend/src/main/java/com/vetos/modules/notification/application/NotificationSendExecutor.java` — importlara ekle:
```java
import java.time.Duration;
import java.time.Instant;
```
Sınıfın içine (alanlardan sonra, `attemptSend`'den önce) ekle:
```java
private static final Duration[] RETRY_BACKOFF = {
    Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6)
};
// attemptCount 1..4 -> otomatik retry planlanir; 5. denemede (attemptCount==5) nextRetryAt=null,
// yani en fazla 4 otomatik retry (toplam 5 deneme), sonrasinda sadece manuel "Tekrar Dene".

Instant computeNextRetryAt(int attemptCountAfterThisFailure) {
    int index = attemptCountAfterThisFailure - 1;
    if (index >= RETRY_BACKOFF.length) return null;
    return Instant.now().plus(RETRY_BACKOFF[index]);
}
```
(Paket-özel görünürlük `NotificationSendExecutor` sınıfıyla aynı — test aynı pakette olduğu için erişebilir.)

`attemptSend` içindeki (Task 2'de geçici olarak `markFailed(null)` yapılan) satırı değiştir:
```java
if (outcome.success()) {
    notificationLog.markSent();
} else {
    notificationLog.markFailed(computeNextRetryAt(notificationLog.getAttemptCount() + 1));
    log.warn("Bildirim gonderimi basarisiz: logId={}, sebep={}", logId, outcome.message());
}
notificationLogRepository.save(notificationLog);
```

- [ ] **Step 5: Backoff testini yaz**

`backend/src/test/java/com/vetos/modules/notification/application/NotificationSendExecutorBackoffTest.java`:
```java
package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationSendPort;
import com.vetos.modules.tenant.domain.TenantLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class NotificationSendExecutorBackoffTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationSendPort notificationSendPort;
    @Mock private TenantLookupPort tenantLookupPort;

    @Test
    void should_scheduleShortBackoff_when_firstFailure() {
        NotificationSendExecutor executor =
            new NotificationSendExecutor(notificationLogRepository, notificationSendPort, tenantLookupPort);
        Instant before = Instant.now();

        Instant nextRetry = executor.computeNextRetryAt(1);

        assertThat(nextRetry).isAfter(before.plusSeconds(60)).isBefore(before.plusSeconds(180));
    }

    @Test
    void should_scheduleLongBackoff_when_fourthFailure() {
        NotificationSendExecutor executor =
            new NotificationSendExecutor(notificationLogRepository, notificationSendPort, tenantLookupPort);
        Instant before = Instant.now();

        Instant nextRetry = executor.computeNextRetryAt(4);

        assertThat(nextRetry).isAfter(before.plusSeconds(3 * 3600)).isBefore(before.plusSeconds(9 * 3600));
    }

    @Test
    void should_returnNull_when_fifthFailure_automaticRetriesExhausted() {
        NotificationSendExecutor executor =
            new NotificationSendExecutor(notificationLogRepository, notificationSendPort, tenantLookupPort);

        Instant nextRetry = executor.computeNextRetryAt(5);

        assertThat(nextRetry).isNull();
    }
}
```

- [ ] **Step 6: `RetryDueNotificationsUseCase` oluştur**

`backend/src/main/java/com/vetos/modules/notification/application/RetryDueNotificationsUseCase.java`:
```java
package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetryDueNotificationsUseCase {
    private static final int BATCH_SIZE = 50;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationSendExecutor notificationSendExecutor;

    @Transactional
    public int execute() {
        List<NotificationLog> claimed = notificationLogRepository.claimDueForRetry(Instant.now(), BATCH_SIZE);
        for (NotificationLog log : claimed) {
            log.markRetrying();
            notificationLogRepository.save(log);
        }
        List<UUID> ids = claimed.stream().map(NotificationLog::getId).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ids.forEach(notificationSendExecutor::attemptSend);
                }
            });
        }
        return claimed.size();
    }
}
```

- [ ] **Step 7: `RetryDueNotificationsUseCase` testini yaz**

`backend/src/test/java/com/vetos/modules/notification/application/RetryDueNotificationsUseCaseTest.java`:
```java
package com.vetos.modules.notification.application;

import com.vetos.modules.notification.domain.NotificationChannel;
import com.vetos.modules.notification.domain.NotificationLog;
import com.vetos.modules.notification.domain.NotificationLogRepository;
import com.vetos.modules.notification.domain.NotificationType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryDueNotificationsUseCaseTest {

    @Mock private NotificationLogRepository notificationLogRepository;
    @Mock private NotificationSendExecutor notificationSendExecutor;

    private NotificationLog aFailedLog() {
        NotificationLog log = NotificationLog.queue(
            UUID.randomUUID(), UUID.randomUUID(), null, NotificationChannel.SMS, NotificationType.APPOINTMENT_REMINDER,
            "05551234567", "mesaj", null, null
        );
        log.markFailed(Instant.now());
        return log;
    }

    @Test
    void should_markClaimedLogsRetrying_and_returnClaimedCount() {
        RetryDueNotificationsUseCase useCase =
            new RetryDueNotificationsUseCase(notificationLogRepository, notificationSendExecutor);
        NotificationLog log1 = aFailedLog();
        NotificationLog log2 = aFailedLog();
        when(notificationLogRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of(log1, log2));

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isEqualTo(2);
        ArgumentCaptor<NotificationLog> captor = ArgumentCaptor.forClass(NotificationLog.class);
        verify(notificationLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(l ->
            assertThat(l.getStatus()).isEqualTo(com.vetos.modules.notification.domain.NotificationStatus.PENDING));
    }

    @Test
    void should_returnZero_when_nothingDue() {
        RetryDueNotificationsUseCase useCase =
            new RetryDueNotificationsUseCase(notificationLogRepository, notificationSendExecutor);
        when(notificationLogRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of());

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isZero();
        verify(notificationLogRepository, never()).save(any());
    }
}
```

- [ ] **Step 8: `NotificationRetryScheduler` oluştur**

`backend/src/main/java/com/vetos/modules/notification/infrastructure/scheduling/NotificationRetryScheduler.java`:
```java
package com.vetos.modules.notification.infrastructure.scheduling;

import com.vetos.modules.notification.application.RetryDueNotificationsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class NotificationRetryScheduler {
    private final RetryDueNotificationsUseCase retryDueNotificationsUseCase;

    @Scheduled(fixedDelay = 120_000) // 2 dakika
    public void retryDue() {
        int retried = retryDueNotificationsUseCase.execute();
        if (retried > 0) {
            log.info("Otomatik retry kuyruklandi: adet={}", retried);
        }
    }
}
```

- [ ] **Step 9: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/notification/domain/NotificationLogRepository.java \
  backend/src/main/java/com/vetos/modules/notification/infrastructure/persistence/NotificationLogJpaRepository.java \
  backend/src/main/java/com/vetos/modules/notification/infrastructure/persistence/NotificationLogRepositoryAdapter.java \
  backend/src/main/java/com/vetos/modules/notification/application/NotificationSendExecutor.java \
  backend/src/main/java/com/vetos/modules/notification/application/RetryDueNotificationsUseCase.java \
  backend/src/main/java/com/vetos/modules/notification/infrastructure/scheduling/NotificationRetryScheduler.java \
  backend/src/test/java/com/vetos/modules/notification/application/NotificationSendExecutorBackoffTest.java \
  backend/src/test/java/com/vetos/modules/notification/application/RetryDueNotificationsUseCaseTest.java
git commit -m "feat: bildirim otomatik retry supurmesi (backoff + FOR UPDATE SKIP LOCKED)"
```

---

## Task 4: `TarbilSyncLog` — `tenant_id` + Retry Alanları

**Files:**
- Create: `backend/src/main/resources/db/migration/V53__tarbil_sync_log_tenant_id_and_retry.sql`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncLog.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/application/QueueTarbilSyncUseCase.java`
- Create: `backend/src/test/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncLogTest.java`

**Interfaces:**
- Consumes: `TenantContext.current(): UUID` (mevcut, Kiracı İzolasyonu turundan).
- Produces: `TarbilSyncLog.queue(UUID tenantId, UUID patientId, TarbilSyncType syncType, String payload)`, `getTenantId(): UUID`, `markFailed(Instant)`, `getAttemptCount()`, `getNextRetryAt()` — Task 5/6 bunları kullanır.

**Not (sıralama):** Bu migration, Kiracı İzolasyonu turunun `V36__patients_tenant_id.sql`'inden SONRA çalışmalıdır (backfill `patients.tenant_id`'ye bağımlı) — dosya numarası `V53`, `V36`'dan büyük olduğu için sıralama zaten doğru.

- [ ] **Step 1: Migration oluştur**

`backend/src/main/resources/db/migration/V53__tarbil_sync_log_tenant_id_and_retry.sql`:
```sql
-- tenant_id, patients.tenant_id uzerinden geriye doldurulur (bkz.
-- docs/superpowers/specs/2026-09-19-arka-plan-is-guvenilirligi-genelleme-design.md S4).
-- TarbilSyncLog Hibernate @TenantId DISINDA kalmaya devam ediyor (NotificationLog
-- ile ayni karar) -- bu duz bir sutun, manuel kontrol icin.
ALTER TABLE tarbil_sync_log ADD COLUMN tenant_id UUID;
UPDATE tarbil_sync_log t SET tenant_id = p.tenant_id FROM patients p WHERE t.patient_id = p.id;
ALTER TABLE tarbil_sync_log ALTER COLUMN tenant_id SET NOT NULL;
CREATE INDEX idx_tarbil_sync_log_tenant_id ON tarbil_sync_log (tenant_id);

ALTER TABLE tarbil_sync_log ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE tarbil_sync_log ADD COLUMN next_retry_at TIMESTAMPTZ;
CREATE INDEX idx_tarbil_sync_log_due_retry ON tarbil_sync_log (next_retry_at)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;
```

- [ ] **Step 2: Domain testini yaz**

`backend/src/test/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncLogTest.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TarbilSyncLogTest {

    private TarbilSyncLog aLog() {
        return TarbilSyncLog.queue(UUID.randomUUID(), UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");
    }

    @Test
    void should_storeTenantId_when_queued() {
        UUID tenantId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queue(tenantId, UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");

        assertThat(log.getTenantId()).isEqualTo(tenantId);
    }

    @Test
    void should_incrementAttemptCount_and_setNextRetryAt_when_markFailed() {
        TarbilSyncLog log = aLog();
        Instant nextRetry = Instant.now().plusSeconds(120);

        log.markFailed(nextRetry);

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.FAILED);
        assertThat(log.getAttemptCount()).isEqualTo(1);
        assertThat(log.getNextRetryAt()).isEqualTo(nextRetry);
    }

    @Test
    void should_clearNextRetryAt_when_markRetrying() {
        TarbilSyncLog log = aLog();
        log.markFailed(Instant.now().plusSeconds(120));

        log.markRetrying();

        assertThat(log.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
        assertThat(log.getNextRetryAt()).isNull();
    }
}
```

- [ ] **Step 3: Testi çalıştır, derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=TarbilSyncLogTest`
Expected: derleme hatası — `queue(UUID, UUID, ...)`, `getTenantId()`, `markFailed(Instant)` henüz yok.

- [ ] **Step 4: `TarbilSyncLog` entity'sini güncelle**

`backend/src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncLog.java` — `id` alanından hemen sonra ekle:
```java
@Column(name = "tenant_id", nullable = false)
private UUID tenantId;
```
`attemptedAt` alanından sonra ekle:
```java
@Column(name = "attempt_count", nullable = false)
private int attemptCount;

@Column(name = "next_retry_at")
private Instant nextRetryAt;
```
`queue` factory'sini değiştir:
```java
public static TarbilSyncLog queue(UUID tenantId, UUID patientId, TarbilSyncType syncType, String payload) {
    TarbilSyncLog log = new TarbilSyncLog();
    log.tenantId = tenantId;
    log.patientId = patientId;
    log.syncType = syncType;
    log.payload = payload;
    log.status = TarbilSyncStatus.PENDING;
    log.attemptedAt = Instant.now();
    return log;
}
```
`markFailed`/`markRetrying` metotlarını değiştir:
```java
public void markFailed(Instant nextRetryAt) {
    this.status = TarbilSyncStatus.FAILED;
    this.attemptedAt = Instant.now();
    this.attemptCount++;
    this.nextRetryAt = nextRetryAt;
}

public void markRetrying() {
    this.status = TarbilSyncStatus.PENDING;
    this.attemptedAt = Instant.now();
    this.nextRetryAt = null;
}
```

- [ ] **Step 5: `QueueTarbilSyncUseCase`'i güncelle (tek üretim çağrısı)**

`backend/src/main/java/com/vetos/modules/integration/tarbil/application/QueueTarbilSyncUseCase.java` — importa ekle:
```java
import com.vetos.platform.tenancy.TenantContext;
```
`execute` metodundaki `TarbilSyncLog.queue(...)` çağrısını değiştir:
```java
@Transactional
public UUID execute(UUID patientId, TarbilSyncType syncType, String payload) {
    // Cagiranlar (PatientIdentificationUpdatedEventListener, VaccinationRecordedEventListener)
    // her zaman authenticate edilmis bir HTTP istegi icindeki senkron @EventListener'lardan
    // cagriliyor -- TenantContext zaten kurulu.
    TarbilSyncLog log = tarbilSyncLogRepository.save(TarbilSyncLog.queue(TenantContext.current(), patientId, syncType, payload));
    UUID logId = log.getId();
    // ... geri kalan gövde (transaction-sync dispatch) degismiyor
```
(Metodun geri kalanı — `TransactionSynchronizationManager` bloğu ve `return logId;` — aynen kalır.)

- [ ] **Step 6: Testi tekrar çalıştır, geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=TarbilSyncLogTest`
Expected: 3 test PASS.

- [ ] **Step 7: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS (`TarbilSyncExecutor.attemptSync` içindeki `syncLog.markFailed();` çağrısı artık derlenmiyor olabilir — varsa `syncLog.markFailed(null);` yaparak geçici düzelt, Task 5'te gerçek backoff'la değiştirilecek).

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V53__tarbil_sync_log_tenant_id_and_retry.sql \
  backend/src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncLog.java \
  backend/src/main/java/com/vetos/modules/integration/tarbil/application/QueueTarbilSyncUseCase.java \
  backend/src/main/java/com/vetos/modules/integration/tarbil/application/TarbilSyncExecutor.java \
  backend/src/test/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncLogTest.java
git commit -m "feat: TarbilSyncLog'a tenant_id + attempt_count/next_retry_at ekle"
```

---

## Task 5: TARBİL Otomatik Retry Süpürmesi

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncLogRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/persistence/TarbilSyncLogJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/persistence/TarbilSyncLogRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/application/TarbilSyncExecutor.java`
- Create: `backend/src/main/java/com/vetos/modules/integration/tarbil/application/RetryDueTarbilSyncsUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/scheduling/TarbilRetryScheduler.java`
- Test: `backend/src/test/java/com/vetos/modules/integration/tarbil/application/TarbilSyncExecutorBackoffTest.java`
- Test: `backend/src/test/java/com/vetos/modules/integration/tarbil/application/RetryDueTarbilSyncsUseCaseTest.java`

**Interfaces:**
- Consumes: `TarbilSyncLog.markFailed(Instant)`, `getAttemptCount()` (Task 4).
- Produces: `TarbilSyncLogRepository.claimDueForRetry(Instant now, int limit): List<TarbilSyncLog>`.

Bu görev Task 3'ün (`notification`) birebir aynı iskeletini `tarbil` isimleriyle tekrarlar.

- [ ] **Step 1: Port'a `claimDueForRetry` ekle, `findByTenantId`'yi doğrudan kolona geçir**

`backend/src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncLogRepository.java`:
```java
package com.vetos.modules.integration.tarbil.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TarbilSyncLogRepository {
    TarbilSyncLog save(TarbilSyncLog log);
    Optional<TarbilSyncLog> findById(UUID id);
    List<TarbilSyncLog> findByTenantId(UUID tenantId);
    List<TarbilSyncLog> findByPatientId(UUID patientId);
    List<TarbilSyncLog> claimDueForRetry(Instant now, int limit);
}
```
(Javadoc'taki "TARBIL_SYNC_LOG'da tenant_id yok" yorumu artık doğru değil — Task 4'te eklendi, yorumu kaldır.)

- [ ] **Step 2: JPA repository'yi güncelle — join kaldır, claim sorgusunu ekle**

`backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/persistence/TarbilSyncLogJpaRepository.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.persistence;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

interface TarbilSyncLogJpaRepository extends JpaRepository<TarbilSyncLog, UUID> {

    List<TarbilSyncLog> findByPatientId(UUID patientId);

    List<TarbilSyncLog> findByTenantId(UUID tenantId);

    @Query(value = """
        SELECT * FROM tarbil_sync_log
        WHERE status = 'FAILED' AND next_retry_at IS NOT NULL AND next_retry_at <= :now
        ORDER BY next_retry_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<TarbilSyncLog> claimDueForRetry(@Param("now") Instant now, @Param("limit") int limit);
}
```
(`findByTenantId`, artık `tarbil_sync_log.tenant_id` doğrudan var olduğu için Spring Data'nın türetilmiş sorgu desteğiyle basit bir metoda dönüşüyor — eski native join sorgusu kaldırılıyor.)

- [ ] **Step 3: Adaptöre `claimDueForRetry`'yi ekle**

`backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/persistence/TarbilSyncLogRepositoryAdapter.java` içine ekle (dosyanın tepesine `import java.time.Instant;` ekle):
```java
@Override
public List<TarbilSyncLog> claimDueForRetry(Instant now, int limit) {
    return jpaRepository.claimDueForRetry(now, limit);
}
```

- [ ] **Step 4: `TarbilSyncExecutor`'a backoff hesaplamasını ekle**

`backend/src/main/java/com/vetos/modules/integration/tarbil/application/TarbilSyncExecutor.java` — importlara ekle:
```java
import java.time.Duration;
import java.time.Instant;
```
Sınıfın içine ekle:
```java
private static final Duration[] RETRY_BACKOFF = {
    Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6)
};

Instant computeNextRetryAt(int attemptCountAfterThisFailure) {
    int index = attemptCountAfterThisFailure - 1;
    if (index >= RETRY_BACKOFF.length) return null;
    return Instant.now().plus(RETRY_BACKOFF[index]);
}
```
`attemptSync` içindeki (Task 4'te geçici olarak `markFailed(null)` yapılmışsa) satırı değiştir:
```java
if (outcome.success()) {
    syncLog.markSynced();
} else {
    syncLog.markFailed(computeNextRetryAt(syncLog.getAttemptCount() + 1));
    log.warn("TARBIL senkronu basarisiz: logId={}, sebep={}", logId, outcome.message());
}
tarbilSyncLogRepository.save(syncLog);
```

- [ ] **Step 5: Backoff testini yaz**

`backend/src/test/java/com/vetos/modules/integration/tarbil/application/TarbilSyncExecutorBackoffTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TarbilSyncExecutorBackoffTest {

    @Mock private TarbilSyncLogRepository tarbilSyncLogRepository;
    @Mock private TarbilSyncPort tarbilSyncPort;

    @Test
    void should_scheduleShortBackoff_when_firstFailure() {
        TarbilSyncExecutor executor = new TarbilSyncExecutor(tarbilSyncLogRepository, tarbilSyncPort);
        Instant before = Instant.now();

        Instant nextRetry = executor.computeNextRetryAt(1);

        assertThat(nextRetry).isAfter(before.plusSeconds(60)).isBefore(before.plusSeconds(180));
    }

    @Test
    void should_returnNull_when_fifthFailure_automaticRetriesExhausted() {
        TarbilSyncExecutor executor = new TarbilSyncExecutor(tarbilSyncLogRepository, tarbilSyncPort);

        Instant nextRetry = executor.computeNextRetryAt(5);

        assertThat(nextRetry).isNull();
    }
}
```

- [ ] **Step 6: `RetryDueTarbilSyncsUseCase` oluştur**

`backend/src/main/java/com/vetos/modules/integration/tarbil/application/RetryDueTarbilSyncsUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetryDueTarbilSyncsUseCase {
    private static final int BATCH_SIZE = 50;
    private final TarbilSyncLogRepository tarbilSyncLogRepository;
    private final TarbilSyncExecutor tarbilSyncExecutor;

    @Transactional
    public int execute() {
        List<TarbilSyncLog> claimed = tarbilSyncLogRepository.claimDueForRetry(Instant.now(), BATCH_SIZE);
        for (TarbilSyncLog log : claimed) {
            log.markRetrying();
            tarbilSyncLogRepository.save(log);
        }
        List<UUID> ids = claimed.stream().map(TarbilSyncLog::getId).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ids.forEach(tarbilSyncExecutor::attemptSync);
                }
            });
        }
        return claimed.size();
    }
}
```

- [ ] **Step 7: `RetryDueTarbilSyncsUseCase` testini yaz**

`backend/src/test/java/com/vetos/modules/integration/tarbil/application/RetryDueTarbilSyncsUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncStatus;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryDueTarbilSyncsUseCaseTest {

    @Mock private TarbilSyncLogRepository tarbilSyncLogRepository;
    @Mock private TarbilSyncExecutor tarbilSyncExecutor;

    private TarbilSyncLog aFailedLog() {
        TarbilSyncLog log = TarbilSyncLog.queue(UUID.randomUUID(), UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");
        log.markFailed(Instant.now());
        return log;
    }

    @Test
    void should_markClaimedLogsRetrying_and_returnClaimedCount() {
        RetryDueTarbilSyncsUseCase useCase = new RetryDueTarbilSyncsUseCase(tarbilSyncLogRepository, tarbilSyncExecutor);
        TarbilSyncLog log1 = aFailedLog();
        when(tarbilSyncLogRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of(log1));

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isEqualTo(1);
        assertThat(log1.getStatus()).isEqualTo(TarbilSyncStatus.PENDING);
        verify(tarbilSyncLogRepository).save(log1);
    }

    @Test
    void should_returnZero_when_nothingDue() {
        RetryDueTarbilSyncsUseCase useCase = new RetryDueTarbilSyncsUseCase(tarbilSyncLogRepository, tarbilSyncExecutor);
        when(tarbilSyncLogRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of());

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isZero();
        verify(tarbilSyncLogRepository, never()).save(any());
    }
}
```

- [ ] **Step 8: `TarbilRetryScheduler` oluştur**

`backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/scheduling/TarbilRetryScheduler.java`:
```java
package com.vetos.modules.integration.tarbil.infrastructure.scheduling;

import com.vetos.modules.integration.tarbil.application.RetryDueTarbilSyncsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class TarbilRetryScheduler {
    private final RetryDueTarbilSyncsUseCase retryDueTarbilSyncsUseCase;

    @Scheduled(fixedDelay = 120_000) // 2 dakika
    public void retryDue() {
        int retried = retryDueTarbilSyncsUseCase.execute();
        if (retried > 0) {
            log.info("TARBIL otomatik retry kuyruklandi: adet={}", retried);
        }
    }
}
```

- [ ] **Step 9: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/integration/tarbil/domain/TarbilSyncLogRepository.java \
  backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/persistence/TarbilSyncLogJpaRepository.java \
  backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/persistence/TarbilSyncLogRepositoryAdapter.java \
  backend/src/main/java/com/vetos/modules/integration/tarbil/application/TarbilSyncExecutor.java \
  backend/src/main/java/com/vetos/modules/integration/tarbil/application/RetryDueTarbilSyncsUseCase.java \
  backend/src/main/java/com/vetos/modules/integration/tarbil/infrastructure/scheduling/TarbilRetryScheduler.java \
  backend/src/test/java/com/vetos/modules/integration/tarbil/application/TarbilSyncExecutorBackoffTest.java \
  backend/src/test/java/com/vetos/modules/integration/tarbil/application/RetryDueTarbilSyncsUseCaseTest.java
git commit -m "feat: TARBIL otomatik retry supurmesi (backoff + FOR UPDATE SKIP LOCKED)"
```

---

## Task 6: TARBİL Manuel Retry Endpoint'ine Kiracı Kontrolü

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/application/RetryTarbilSyncUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/tarbil/api/TarbilController.java`
- Create: `backend/src/test/java/com/vetos/modules/integration/tarbil/application/RetryTarbilSyncUseCaseTest.java`

**Interfaces:**
- Consumes: `TarbilSyncLog.getTenantId()` (Task 4), `TenantContext.current(): UUID` (mevcut).

- [ ] **Step 1: `RetryTarbilSyncUseCase`'e kiracı kontrolü ekle**

`backend/src/main/java/com/vetos/modules/integration/tarbil/application/RetryTarbilSyncUseCase.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSyncLogNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetryTarbilSyncUseCase {

    private final TarbilSyncLogRepository tarbilSyncLogRepository;
    private final TarbilSyncExecutor tarbilSyncExecutor;

    /**
     * TarbilSyncLog @TenantId DISINDA tutuluyor (NotificationLog ile ayni
     * karar) -- bu yuzden kiraci kontrolu ELLE yapilir. Baska kiracinin
     * kaydi, mevcut TarbilSyncLogNotFoundException (404) ile "yok" gibi
     * gorunur; var oldugu bile sizdirilmaz.
     */
    @Transactional
    public void execute(UUID tenantId, UUID logId) {
        TarbilSyncLog log = tarbilSyncLogRepository.findById(logId)
            .filter(l -> l.getTenantId().equals(tenantId))
            .orElseThrow(() -> new TarbilSyncLogNotFoundException(logId));
        log.markRetrying();
        tarbilSyncLogRepository.save(log);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tarbilSyncExecutor.attemptSync(logId);
                }
            });
        } else {
            tarbilSyncExecutor.attemptSync(logId);
        }
    }
}
```

- [ ] **Step 2: `TarbilController.retry`'yi güncelle**

`backend/src/main/java/com/vetos/modules/integration/tarbil/api/TarbilController.java` içindeki `retry` metodunu değiştir:
```java
@PostMapping("/sync-logs/{id}/retry")
public void retry(@PathVariable UUID id) {
    retryTarbilSyncUseCase.execute(TenantContext.current(), id);
}
```

- [ ] **Step 3: `RetryTarbilSyncUseCase` testini yaz**

`backend/src/test/java/com/vetos/modules/integration/tarbil/application/RetryTarbilSyncUseCaseTest.java`:
```java
package com.vetos.modules.integration.tarbil.application;

import com.vetos.modules.integration.tarbil.domain.TarbilSyncLog;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncLogRepository;
import com.vetos.modules.integration.tarbil.domain.TarbilSyncType;
import com.vetos.modules.integration.tarbil.domain.exception.TarbilSyncLogNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetryTarbilSyncUseCaseTest {

    @Mock private TarbilSyncLogRepository tarbilSyncLogRepository;
    @Mock private TarbilSyncExecutor tarbilSyncExecutor;

    @Test
    void should_throwNotFound_when_logBelongsToAnotherTenant() {
        RetryTarbilSyncUseCase useCase = new RetryTarbilSyncUseCase(tarbilSyncLogRepository, tarbilSyncExecutor);
        UUID callerTenantId = UUID.randomUUID();
        UUID foreignTenantId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queue(foreignTenantId, UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");
        when(tarbilSyncLogRepository.findById(logId)).thenReturn(Optional.of(log));

        assertThatThrownBy(() -> useCase.execute(callerTenantId, logId))
            .isInstanceOf(TarbilSyncLogNotFoundException.class);

        verify(tarbilSyncLogRepository, never()).save(any());
        verify(tarbilSyncExecutor, never()).attemptSync(any());
    }

    @Test
    void should_retry_when_logBelongsToCallerTenant() {
        RetryTarbilSyncUseCase useCase = new RetryTarbilSyncUseCase(tarbilSyncLogRepository, tarbilSyncExecutor);
        UUID tenantId = UUID.randomUUID();
        UUID logId = UUID.randomUUID();
        TarbilSyncLog log = TarbilSyncLog.queue(tenantId, UUID.randomUUID(), TarbilSyncType.VACCINATION, "{}");
        when(tarbilSyncLogRepository.findById(logId)).thenReturn(Optional.of(log));

        useCase.execute(tenantId, logId);

        verify(tarbilSyncLogRepository).save(log);
        verify(tarbilSyncExecutor).attemptSync(logId);
    }
}
```
(Not: `logId`, `TarbilSyncLog.queue(...)` `@GeneratedValue` ile üretilen `id`'yi gerçek bir persist olmadan set etmez — testte `findById(logId)` sadece stub'landığı için `log`'un kendi `id` alanı `null` kalabilir, bu testin doğrulamasını etkilemez çünkü karşılaştırma `tenantId` üzerinden yapılıyor. `verify(tarbilSyncExecutor).attemptSync(logId)` çağrısı, use case'in `attemptSync`'i **parametre olarak gelen `logId`** ile çağırdığını doğrular, entity'nin kendi `id`'siyle değil — koddaki `tarbilSyncExecutor.attemptSync(logId)` zaten metodun parametresini kullanıyor.)

- [ ] **Step 4: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/integration/tarbil/application/RetryTarbilSyncUseCase.java \
  backend/src/main/java/com/vetos/modules/integration/tarbil/api/TarbilController.java \
  backend/src/test/java/com/vetos/modules/integration/tarbil/application/RetryTarbilSyncUseCaseTest.java
git commit -m "fix: RetryTarbilSyncUseCase'e elle kiraci kontrolu (TarbilSyncLog istisnasi)"
```

---

## Task 7: `EInvoiceSubmission` — Retry Alanları

**Files:**
- Create: `backend/src/main/resources/db/migration/V54__efatura_submission_retry.sql`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmission.java`
- Create: `backend/src/test/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmissionRetryTest.java`

**Interfaces:**
- Produces: `EInvoiceSubmission.markFailed(String reason, Instant nextRetryAt)` (eski `markFailed(String)` imzasının yerine geçer), `getAttemptCount()`, `getNextRetryAt()` — Task 8/9/10 bunları kullanır.

`EInvoiceSubmission` zaten `tenant_id`'ye sahip — bu görev sadece retry alanlarını ekler.

- [ ] **Step 1: Migration oluştur**

`backend/src/main/resources/db/migration/V54__efatura_submission_retry.sql`:
```sql
ALTER TABLE efatura_submission ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE efatura_submission ADD COLUMN next_retry_at TIMESTAMPTZ;
CREATE INDEX idx_efatura_submission_due_retry ON efatura_submission (next_retry_at)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;
```

- [ ] **Step 2: Domain testini yaz**

`backend/src/test/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmissionRetryTest.java`:
```java
package com.vetos.modules.integration.efatura.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EInvoiceSubmissionRetryTest {

    private EInvoiceSubmission aSubmission() {
        return EInvoiceSubmission.queue(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            new BigDecimal("120.00"), new BigDecimal("20.00")
        );
    }

    @Test
    void should_incrementAttemptCount_and_setNextRetryAt_when_markFailed() {
        EInvoiceSubmission submission = aSubmission();
        Instant nextRetry = Instant.now().plusSeconds(120);

        submission.markFailed("saglayici hatasi", nextRetry);

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.FAILED);
        assertThat(submission.getFailureReason()).isEqualTo("saglayici hatasi");
        assertThat(submission.getAttemptCount()).isEqualTo(1);
        assertThat(submission.getNextRetryAt()).isEqualTo(nextRetry);
    }

    @Test
    void should_clearNextRetryAt_when_markRetrying() {
        EInvoiceSubmission submission = aSubmission();
        submission.markFailed("hata", Instant.now().plusSeconds(120));

        submission.markRetrying();

        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.PENDING);
        assertThat(submission.getNextRetryAt()).isNull();
    }
}
```

- [ ] **Step 3: Testi çalıştır, derleme hatasıyla başarısız olduğunu doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=EInvoiceSubmissionRetryTest`
Expected: derleme hatası — `markFailed(String, Instant)` henüz yok.

- [ ] **Step 4: `EInvoiceSubmission` entity'sini güncelle**

`backend/src/main/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmission.java` — `attemptedAt` alanından hemen önce ekle:
```java
@Column(name = "attempt_count", nullable = false)
private int attemptCount;

@Column(name = "next_retry_at")
private Instant nextRetryAt;
```
`markFailed`/`markRetrying` metotlarını değiştir:
```java
public void markFailed(String reason, Instant nextRetryAt) {
    this.status = EInvoiceSubmissionStatus.FAILED;
    this.failureReason = reason;
    this.attemptedAt = Instant.now();
    this.attemptCount++;
    this.nextRetryAt = nextRetryAt;
}

public void markRetrying() {
    this.status = EInvoiceSubmissionStatus.PENDING;
    this.failureReason = null;
    this.attemptedAt = Instant.now();
    this.nextRetryAt = null;
}
```

- [ ] **Step 5: Çağrı sitelerini geçici olarak düzelt (derleme kırılmasın diye)**

`backend/src/main/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutor.java` içindeki `submission.markFailed(outcome.message());` satırını `submission.markFailed(outcome.message(), null);` yap.
`backend/src/main/java/com/vetos/modules/integration/efatura/application/ApplyEInvoiceCallbackUseCase.java` içindeki `submission.markFailed(outcome.message());` satırını `submission.markFailed(outcome.message(), null);` yap.
(Her ikisi de Task 8'de gerçek backoff hesaplamasıyla — sadece `EInvoiceSubmissionExecutor`'daki — değiştirilecek; `ApplyEInvoiceCallbackUseCase`'deki `null` kalıcıdır, çünkü sağlayıcı zaten resmi bir sonuç bildirmiş, otomatik retry'ye gerek yok.)

- [ ] **Step 6: Testi tekrar çalıştır, geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test -Dtest=EInvoiceSubmissionRetryTest`
Expected: 2 test PASS.

- [ ] **Step 7: Etkilenen mevcut testleri güncelle**

`backend/src/test/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutorTest.java` ve `ApplyEInvoiceCallbackUseCaseTest.java` içinde `markFailed(...)` çağrısının eski tek-parametreli imzasına dayanan bir doğrulama varsa (örn. `verify(...).markFailed(anyString())` gibi bir mock beklentisi — gerçek entity kullanıldığı için büyük olasılıkla yok, ama olası bir `any(String.class)` yerine `any(String.class), any()` gerekebilir), derleme hatası çıkarsa ilgili satırı yeni imzaya (`String, Instant`) uyacak şekilde düzelt.

Run: `cd backend && ./mvnw -q test`
Expected: PASS. Derleme hatası varsa yukarıdaki gibi düzelt ve tekrar çalıştır.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/resources/db/migration/V54__efatura_submission_retry.sql \
  backend/src/main/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmission.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutor.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/application/ApplyEInvoiceCallbackUseCase.java \
  backend/src/test/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmissionRetryTest.java
git commit -m "feat: EInvoiceSubmission'a attempt_count/next_retry_at ekle"
```

---

## Task 8: e-Fatura Otomatik Retry Süpürmesi (PROCESSING Hariç)

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmissionRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionRepositoryAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutor.java`
- Create: `backend/src/main/java/com/vetos/modules/integration/efatura/application/RetryDueEInvoiceSubmissionsUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/scheduling/EInvoiceRetryScheduler.java`
- Test: `backend/src/test/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutorBackoffTest.java`
- Test: `backend/src/test/java/com/vetos/modules/integration/efatura/application/RetryDueEInvoiceSubmissionsUseCaseTest.java`

**Interfaces:**
- Consumes: `EInvoiceSubmission.markFailed(String, Instant)`, `getAttemptCount()` (Task 7).
- Produces: `EInvoiceSubmissionRepository.claimDueForRetry(Instant now, int limit): List<EInvoiceSubmission>`.

**Kritik davranış garantisi:** claim sorgusu `status = 'PROCESSING'` olan hiçbir kaydı asla döndürmemeli (mükerrer GIB gönderimi riski) — bu görev bunu ayrı bir testle doğrular.

- [ ] **Step 1: Port'a `claimDueForRetry` ekle**

`backend/src/main/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmissionRepository.java`:
```java
package com.vetos.modules.integration.efatura.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EInvoiceSubmissionRepository {
    EInvoiceSubmission save(EInvoiceSubmission submission);
    Optional<EInvoiceSubmission> findById(UUID id);
    List<EInvoiceSubmission> findByTenantId(UUID tenantId);
    Optional<EInvoiceSubmission> findByProviderReference(String providerReference);
    List<EInvoiceSubmission> claimDueForRetry(Instant now, int limit);
}
```

- [ ] **Step 2: JPA repository'ye native sorgu ekle**

`backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionJpaRepository.java`:
```java
package com.vetos.modules.integration.efatura.infrastructure.persistence;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface EInvoiceSubmissionJpaRepository extends JpaRepository<EInvoiceSubmission, UUID> {
    List<EInvoiceSubmission> findByTenantId(UUID tenantId);
    Optional<EInvoiceSubmission> findByProviderReference(String providerReference);

    // status = 'FAILED' filtresi KESIN -- PROCESSING asla claim edilmez (mukerrer GIB gonderimi riski).
    @Query(value = """
        SELECT * FROM efatura_submission
        WHERE status = 'FAILED' AND next_retry_at IS NOT NULL AND next_retry_at <= :now
        ORDER BY next_retry_at
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<EInvoiceSubmission> claimDueForRetry(@Param("now") Instant now, @Param("limit") int limit);
}
```

- [ ] **Step 3: Adaptöre `claimDueForRetry`'yi ekle**

`backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionRepositoryAdapter.java` içine ekle (tepeye `import java.time.Instant;` ekle):
```java
@Override
public List<EInvoiceSubmission> claimDueForRetry(Instant now, int limit) {
    return jpaRepository.claimDueForRetry(now, limit);
}
```

- [ ] **Step 4: `EInvoiceSubmissionExecutor`'a backoff hesaplamasını ekle**

`backend/src/main/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutor.java` — importlara ekle:
```java
import java.time.Duration;
import java.time.Instant;
```
Sınıfın içine ekle (`DEFAULT_UNIT` sabitinden sonra):
```java
private static final Duration[] RETRY_BACKOFF = {
    Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6)
};

Instant computeNextRetryAt(int attemptCountAfterThisFailure) {
    int index = attemptCountAfterThisFailure - 1;
    if (index >= RETRY_BACKOFF.length) return null;
    return Instant.now().plus(RETRY_BACKOFF[index]);
}
```
`attemptSubmit` içindeki (Task 7'de geçici olarak `null` yapılan) satırı değiştir:
```java
if (!outcome.success()) {
    submission.markFailed(outcome.message(), computeNextRetryAt(submission.getAttemptCount() + 1));
    log.warn("e-Fatura gonderimi basarisiz: submissionId={}, sebep={}", submissionId, outcome.message());
} else if (outcome.finalResult()) {
```
(Sadece `if (!outcome.success())` dalındaki satır değişiyor, `else if`/`else` dalları aynen kalıyor.)

- [ ] **Step 5: Backoff testini yaz**

`backend/src/test/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutorBackoffTest.java`:
```java
package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.billing.domain.InvoiceEInvoiceUpdatePort;
import com.vetos.modules.billing.domain.InvoiceLineRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceGatewayPort;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.patient.domain.OwnerLookupPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EInvoiceSubmissionExecutorBackoffTest {

    @Mock private EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    @Mock private EInvoiceGatewayPort eInvoiceGatewayPort;
    @Mock private OwnerLookupPort ownerLookupPort;
    @Mock private InvoiceLineRepository invoiceLineRepository;
    @Mock private InvoiceEInvoiceUpdatePort invoiceEInvoiceUpdatePort;

    @Test
    void should_scheduleShortBackoff_when_firstFailure() {
        EInvoiceSubmissionExecutor executor = new EInvoiceSubmissionExecutor(
            eInvoiceSubmissionRepository, eInvoiceGatewayPort, ownerLookupPort, invoiceLineRepository, invoiceEInvoiceUpdatePort
        );
        Instant before = Instant.now();

        Instant nextRetry = executor.computeNextRetryAt(1);

        assertThat(nextRetry).isAfter(before.plusSeconds(60)).isBefore(before.plusSeconds(180));
    }

    @Test
    void should_returnNull_when_fifthFailure_automaticRetriesExhausted() {
        EInvoiceSubmissionExecutor executor = new EInvoiceSubmissionExecutor(
            eInvoiceSubmissionRepository, eInvoiceGatewayPort, ownerLookupPort, invoiceLineRepository, invoiceEInvoiceUpdatePort
        );

        Instant nextRetry = executor.computeNextRetryAt(5);

        assertThat(nextRetry).isNull();
    }
}
```
(`EInvoiceSubmissionExecutor`'ın `callbackBaseUrl` alanı `@Value` ile enjekte ediliyor ve constructor'da yer almıyor — bu testte kullanılmıyor, sorun değil.)

- [ ] **Step 6: `RetryDueEInvoiceSubmissionsUseCase` oluştur**

`backend/src/main/java/com/vetos/modules/integration/efatura/application/RetryDueEInvoiceSubmissionsUseCase.java`:
```java
package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RetryDueEInvoiceSubmissionsUseCase {
    private static final int BATCH_SIZE = 50;
    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final EInvoiceSubmissionExecutor eInvoiceSubmissionExecutor;

    @Transactional
    public int execute() {
        List<EInvoiceSubmission> claimed = eInvoiceSubmissionRepository.claimDueForRetry(Instant.now(), BATCH_SIZE);
        for (EInvoiceSubmission submission : claimed) {
            submission.markRetrying();
            eInvoiceSubmissionRepository.save(submission);
        }
        List<UUID> ids = claimed.stream().map(EInvoiceSubmission::getId).toList();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ids.forEach(eInvoiceSubmissionExecutor::attemptSubmit);
                }
            });
        }
        return claimed.size();
    }
}
```

- [ ] **Step 7: `RetryDueEInvoiceSubmissionsUseCase` testini yaz (PROCESSING garantisi dahil)**

`backend/src/test/java/com/vetos/modules/integration/efatura/application/RetryDueEInvoiceSubmissionsUseCaseTest.java`:
```java
package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceDocumentType;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmission;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RetryDueEInvoiceSubmissionsUseCaseTest {

    @Mock private EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    @Mock private EInvoiceSubmissionExecutor eInvoiceSubmissionExecutor;

    private EInvoiceSubmission aFailedSubmission() {
        EInvoiceSubmission submission = EInvoiceSubmission.queue(
            UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
            new BigDecimal("120.00"), new BigDecimal("20.00")
        );
        submission.markFailed("hata", Instant.now());
        return submission;
    }

    @Test
    void should_markClaimedSubmissionsRetrying_and_returnClaimedCount() {
        RetryDueEInvoiceSubmissionsUseCase useCase =
            new RetryDueEInvoiceSubmissionsUseCase(eInvoiceSubmissionRepository, eInvoiceSubmissionExecutor);
        EInvoiceSubmission submission = aFailedSubmission();
        when(eInvoiceSubmissionRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of(submission));

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isEqualTo(1);
        assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.PENDING);
        verify(eInvoiceSubmissionRepository).save(submission);
    }

    /**
     * Bu testin ADI ve amacı bilinçli olarak vurgulu: repository sahte
     * (mock) olduğu için gerçek "PROCESSING asla claim edilmez" garantisi
     * burada değil, native SQL'in kendisinde (status = 'FAILED' filtresi,
     * bkz. Step 2) sağlanıyor. Bu test yalnızca use-case'in claim edilen
     * HER kaydı -- repository ne dönerse -- körü körüne retry'e soktuğunu,
     * yani use-case katmanında status'e göre ekstra bir filtre OLMADIĞINI
     * doğruluyor; gerçek koruma tamamen sorgunun sorumluluğunda.
     */
    @Test
    void should_returnZero_when_repositoryClaimsNothing() {
        RetryDueEInvoiceSubmissionsUseCase useCase =
            new RetryDueEInvoiceSubmissionsUseCase(eInvoiceSubmissionRepository, eInvoiceSubmissionExecutor);
        when(eInvoiceSubmissionRepository.claimDueForRetry(any(), anyInt())).thenReturn(List.of());

        int claimedCount = useCase.execute();

        assertThat(claimedCount).isZero();
        verify(eInvoiceSubmissionRepository, never()).save(any());
    }
}
```

- [ ] **Step 8: `EInvoiceRetryScheduler` oluştur**

`backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/scheduling/EInvoiceRetryScheduler.java`:
```java
package com.vetos.modules.integration.efatura.infrastructure.scheduling;

import com.vetos.modules.integration.efatura.application.RetryDueEInvoiceSubmissionsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class EInvoiceRetryScheduler {
    private final RetryDueEInvoiceSubmissionsUseCase retryDueEInvoiceSubmissionsUseCase;

    @Scheduled(fixedDelay = 120_000) // 2 dakika
    public void retryDue() {
        int retried = retryDueEInvoiceSubmissionsUseCase.execute();
        if (retried > 0) {
            log.info("e-Fatura otomatik retry kuyruklandi: adet={}", retried);
        }
    }
}
```

- [ ] **Step 9: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmissionRepository.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionJpaRepository.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionRepositoryAdapter.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutor.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/application/RetryDueEInvoiceSubmissionsUseCase.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/scheduling/EInvoiceRetryScheduler.java \
  backend/src/test/java/com/vetos/modules/integration/efatura/application/EInvoiceSubmissionExecutorBackoffTest.java \
  backend/src/test/java/com/vetos/modules/integration/efatura/application/RetryDueEInvoiceSubmissionsUseCaseTest.java
git commit -m "feat: e-Fatura otomatik retry supurmesi (PROCESSING haric, backoff + FOR UPDATE SKIP LOCKED)"
```

---

## Task 9: e-Fatura Manuel Retry Endpoint'ine Kiracı Kontrolü

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/application/RetryEInvoiceSubmissionUseCase.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/api/EInvoiceController.java`
- Modify: `backend/src/test/java/com/vetos/modules/integration/efatura/application/RetryEInvoiceSubmissionUseCaseTest.java`

**Interfaces:**
- Consumes: `EInvoiceSubmission.getTenantId()` (mevcut), `TenantContext.current(): UUID` (mevcut).

- [ ] **Step 1: `RetryEInvoiceSubmissionUseCase`'e kiracı kontrolü ekle**

`backend/src/main/java/com/vetos/modules/integration/efatura/application/RetryEInvoiceSubmissionUseCase.java` içindeki `execute` metodunu değiştir:
```java
@Transactional
public void execute(UUID tenantId, UUID submissionId) {
    EInvoiceSubmission submission = eInvoiceSubmissionRepository.findById(submissionId)
        .filter(s -> s.getTenantId().equals(tenantId))
        .orElseThrow(() -> new EInvoiceSubmissionNotFoundException(submissionId));

    // PROCESSING: saglayici istegi zaten kabul etti, GIB resmilesme
    // callback'i bekleniyor -- tekrar gonderim mukerrer fatura yaratir.
    if (submission.getStatus() == EInvoiceSubmissionStatus.PROCESSING) {
        throw new EInvoiceSubmissionAlreadyProcessingException(submissionId);
    }

    submission.markRetrying();
    eInvoiceSubmissionRepository.save(submission);

    if (TransactionSynchronizationManager.isSynchronizationActive()) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                eInvoiceSubmissionExecutor.attemptSubmit(submissionId);
            }
        });
    } else {
        eInvoiceSubmissionExecutor.attemptSubmit(submissionId);
    }
}
```
(Sınıfın geri kalanı — alanlar, importlar — değişmiyor.)

- [ ] **Step 2: `EInvoiceController.retry`'yi güncelle**

`backend/src/main/java/com/vetos/modules/integration/efatura/api/EInvoiceController.java` içindeki `retry` metodunu değiştir:
```java
@PostMapping("/submissions/{id}/retry")
public void retry(@PathVariable UUID id) {
    retryEInvoiceSubmissionUseCase.execute(TenantContext.current(), id);
}
```

- [ ] **Step 3: Mevcut testi yeni imzaya güncelle + yabancı kiracı testi ekle**

`backend/src/test/java/com/vetos/modules/integration/efatura/application/RetryEInvoiceSubmissionUseCaseTest.java` — her iki mevcut testte `useCase.execute(submissionId)` çağrısını `useCase.execute(tenantId, submissionId)` yap; `aFailedSubmission()`/inline `EInvoiceSubmission.queue(...)` çağrılarında kullanılan ilk parametre (tenantId) zaten `UUID.randomUUID()` — bunu bir değişkene al ve `execute` çağrısına aynısını geçir:
```java
@Test
void should_requeueAndReattempt_when_submissionFailed() {
    UUID tenantId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    EInvoiceSubmission submission = EInvoiceSubmission.queue(
        tenantId, UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
        new BigDecimal("120.00"), new BigDecimal("20.00")
    );
    submission.markFailed("onceki hata mesaji", null);
    when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

    useCase.execute(tenantId, submissionId);

    assertThat(submission.getStatus()).isEqualTo(EInvoiceSubmissionStatus.PENDING);
    verify(eInvoiceSubmissionRepository).save(submission);
    verify(eInvoiceSubmissionExecutor).attemptSubmit(submissionId);
}

@Test
void should_throwAlreadyProcessing_when_submissionAwaitingProviderCallback() {
    UUID tenantId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    EInvoiceSubmission submission = EInvoiceSubmission.queue(
        tenantId, UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
        new BigDecimal("120.00"), new BigDecimal("20.00")
    );
    submission.markAcceptedByProvider("123");
    when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

    assertThatThrownBy(() -> useCase.execute(tenantId, submissionId))
        .isInstanceOf(EInvoiceSubmissionAlreadyProcessingException.class);

    verify(eInvoiceSubmissionRepository, never()).save(any());
    verifyNoInteractions(eInvoiceSubmissionExecutor);
}

@Test
void should_throwNotFound_when_submissionBelongsToAnotherTenant() {
    UUID callerTenantId = UUID.randomUUID();
    UUID foreignTenantId = UUID.randomUUID();
    UUID submissionId = UUID.randomUUID();
    EInvoiceSubmission submission = EInvoiceSubmission.queue(
        foreignTenantId, UUID.randomUUID(), UUID.randomUUID(), EInvoiceDocumentType.E_ARSIV,
        new BigDecimal("120.00"), new BigDecimal("20.00")
    );
    submission.markFailed("hata", null);
    when(eInvoiceSubmissionRepository.findById(submissionId)).thenReturn(Optional.of(submission));

    assertThatThrownBy(() -> useCase.execute(callerTenantId, submissionId))
        .isInstanceOf(com.vetos.modules.integration.efatura.domain.exception.EInvoiceSubmissionNotFoundException.class);

    verify(eInvoiceSubmissionRepository, never()).save(any());
    verifyNoInteractions(eInvoiceSubmissionExecutor);
}
```
(Dosyanın geri kalanındaki `aFailedSubmission()` yardımcı metodu artık kullanılmıyorsa kaldır; importlar aynı kalır.)

- [ ] **Step 4: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/integration/efatura/application/RetryEInvoiceSubmissionUseCase.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/api/EInvoiceController.java \
  backend/src/test/java/com/vetos/modules/integration/efatura/application/RetryEInvoiceSubmissionUseCaseTest.java
git commit -m "fix: RetryEInvoiceSubmissionUseCase'e elle kiraci kontrolu"
```

---

## Task 10: Sıkışan `PROCESSING` e-Fatura Kayıtları İçin Uzlaştırma

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmissionRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionJpaRepository.java`
- Modify: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionRepositoryAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/integration/efatura/application/ReconcileStuckEInvoiceSubmissionsUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/scheduling/EInvoiceReconciliationScheduler.java`
- Test: `backend/src/test/java/com/vetos/modules/integration/efatura/application/ReconcileStuckEInvoiceSubmissionsUseCaseTest.java`

**Interfaces:**
- Consumes: `ApplyEInvoiceCallbackUseCase.execute(String providerReference)` (mevcut, idempotent).

- [ ] **Step 1: Port'a `findProviderReferencesByStatusAndAttemptedAtBefore` ekle**

`backend/src/main/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmissionRepository.java` içine ekle:
```java
List<String> findProviderReferencesByStatusAndAttemptedAtBefore(EInvoiceSubmissionStatus status, Instant threshold);
```
(Dosyanın tepesine zaten Task 8'de `EInvoiceSubmissionStatus` importu eklenmediyse — aynı pakette olduğu için import gerekmez.)

- [ ] **Step 2: JPA repository'ye türetilmiş sorgu ekle**

`backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionJpaRepository.java` içine ekle:
```java
@Query("SELECT s.providerReference FROM EInvoiceSubmission s WHERE s.status = :status AND s.attemptedAt < :threshold")
List<String> findProviderReferencesByStatusAndAttemptedAtBefore(
    @Param("status") com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus status,
    @Param("threshold") Instant threshold
);
```
(JPQL kullanılıyor — bu sorgu native değil, entity alan adlarıyla çalışıyor, bu yüzden proje kuralı olan "repository katmanı için ayrı test yazılmaz" burada da geçerli, JPQL'in kendisi Spring Data tarafından derleme zamanında doğrulanmaz ama `ReconcileStuckEInvoiceSubmissionsUseCaseTest` mock repository ile bu metodu zaten çağırıyor.)

- [ ] **Step 3: Adaptöre delege eden metodu ekle**

`backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionRepositoryAdapter.java` içine ekle:
```java
@Override
public List<String> findProviderReferencesByStatusAndAttemptedAtBefore(EInvoiceSubmissionStatus status, Instant threshold) {
    return jpaRepository.findProviderReferencesByStatusAndAttemptedAtBefore(status, threshold);
}
```
(Gerekirse tepeye `import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;` ekle.)

- [ ] **Step 4: `ReconcileStuckEInvoiceSubmissionsUseCase` oluştur**

`backend/src/main/java/com/vetos/modules/integration/efatura/application/ReconcileStuckEInvoiceSubmissionsUseCase.java`:
```java
package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReconcileStuckEInvoiceSubmissionsUseCase {
    private static final Duration STALE_THRESHOLD = Duration.ofHours(1);

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final ApplyEInvoiceCallbackUseCase applyEInvoiceCallbackUseCase;

    @Transactional
    public int execute() {
        List<String> stale = eInvoiceSubmissionRepository.findProviderReferencesByStatusAndAttemptedAtBefore(
            EInvoiceSubmissionStatus.PROCESSING, Instant.now().minus(STALE_THRESHOLD)
        );
        stale.forEach(applyEInvoiceCallbackUseCase::execute);
        return stale.size();
    }
}
```

- [ ] **Step 5: Testini yaz**

`backend/src/test/java/com/vetos/modules/integration/efatura/application/ReconcileStuckEInvoiceSubmissionsUseCaseTest.java`:
```java
package com.vetos.modules.integration.efatura.application;

import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionRepository;
import com.vetos.modules.integration.efatura.domain.EInvoiceSubmissionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReconcileStuckEInvoiceSubmissionsUseCaseTest {

    @Mock private EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    @Mock private ApplyEInvoiceCallbackUseCase applyEInvoiceCallbackUseCase;

    @Test
    void should_callApplyCallback_forEachStaleProviderReference() {
        ReconcileStuckEInvoiceSubmissionsUseCase useCase =
            new ReconcileStuckEInvoiceSubmissionsUseCase(eInvoiceSubmissionRepository, applyEInvoiceCallbackUseCase);
        when(eInvoiceSubmissionRepository.findProviderReferencesByStatusAndAttemptedAtBefore(eq(EInvoiceSubmissionStatus.PROCESSING), any(Instant.class)))
            .thenReturn(List.of("ref-1", "ref-2"));

        int resolved = useCase.execute();

        assertThat(resolved).isEqualTo(2);
        verify(applyEInvoiceCallbackUseCase).execute("ref-1");
        verify(applyEInvoiceCallbackUseCase).execute("ref-2");
    }

    @Test
    void should_returnZero_and_callNothing_when_noStaleSubmissions() {
        ReconcileStuckEInvoiceSubmissionsUseCase useCase =
            new ReconcileStuckEInvoiceSubmissionsUseCase(eInvoiceSubmissionRepository, applyEInvoiceCallbackUseCase);
        when(eInvoiceSubmissionRepository.findProviderReferencesByStatusAndAttemptedAtBefore(eq(EInvoiceSubmissionStatus.PROCESSING), any(Instant.class)))
            .thenReturn(List.of());

        int resolved = useCase.execute();

        assertThat(resolved).isZero();
        verifyNoInteractions(applyEInvoiceCallbackUseCase);
    }
}
```

- [ ] **Step 6: `EInvoiceReconciliationScheduler` oluştur**

`backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/scheduling/EInvoiceReconciliationScheduler.java`:
```java
package com.vetos.modules.integration.efatura.infrastructure.scheduling;

import com.vetos.modules.integration.efatura.application.ReconcileStuckEInvoiceSubmissionsUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
class EInvoiceReconciliationScheduler {
    private final ReconcileStuckEInvoiceSubmissionsUseCase reconcileStuckEInvoiceSubmissionsUseCase;

    @Scheduled(fixedDelay = 3_600_000) // saatte bir -- FAILED retry'lerden (2dk) cok daha seyrek,
                                        // aciliyeti yok, saglayici API'sini gereksiz yormasin
    public void reconcile() {
        int resolved = reconcileStuckEInvoiceSubmissionsUseCase.execute();
        if (resolved > 0) {
            log.info("e-Fatura uzlastirmasi tetiklendi: adet={}", resolved);
        }
    }
}
```

- [ ] **Step 7: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/integration/efatura/domain/EInvoiceSubmissionRepository.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionJpaRepository.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/persistence/EInvoiceSubmissionRepositoryAdapter.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/application/ReconcileStuckEInvoiceSubmissionsUseCase.java \
  backend/src/main/java/com/vetos/modules/integration/efatura/infrastructure/scheduling/EInvoiceReconciliationScheduler.java \
  backend/src/test/java/com/vetos/modules/integration/efatura/application/ReconcileStuckEInvoiceSubmissionsUseCaseTest.java
git commit -m "feat: sikisan PROCESSING e-Fatura kayitlari icin saatlik uzlastirma"
```

---

## Task 11: `PlatformBillingScheduler` — Advisory Lock

**Files:**
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/scheduling/PlatformBillingScheduler.java`
- Create: `backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/scheduling/PlatformBillingSchedulerTest.java`

**Interfaces:**
- Consumes: `AdvisoryLock.tryAcquire(long key): boolean` (Task 1).

- [ ] **Step 1: `PlatformBillingScheduler`'ı advisory lock ile sarmala**

`backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/scheduling/PlatformBillingScheduler.java`:
```java
package com.vetos.modules.platformadmin.infrastructure.scheduling;

import com.vetos.modules.platformadmin.application.FlagOverdueAndSuspendUseCase;
import com.vetos.modules.platformadmin.application.GenerateDueInvoicesUseCase;
import com.vetos.modules.platformadmin.application.RemindDueSoonInvoicesUseCase;
import com.vetos.platform.concurrency.AdvisoryLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Gunde bir kez calisir -- AppointmentReminderScheduler ile ayni desen.
 * Uc adim sirayla: fatura uret, son-gun hatirlat, gecikeni askiya al. Her
 * adim kendi try/catch'inde -- biri patlarsa digerleri yine de calisir.
 * Advisory lock, coklu instance'ta ayni gunun faturasinin iki kez
 * uretilmesini engeller (bkz. AppointmentReminderScheduler.REMINDER_LOCK_KEY
 * ile ayni desen, farkli bir sabit anahtarla).
 */
@Component
@Slf4j
@RequiredArgsConstructor
class PlatformBillingScheduler {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final long PLATFORM_BILLING_LOCK_KEY = 7_301_002; // REMINDER_LOCK_KEY'den farkli, ayri bir sabit

    private final GenerateDueInvoicesUseCase generateDueInvoicesUseCase;
    private final RemindDueSoonInvoicesUseCase remindDueSoonInvoicesUseCase;
    private final FlagOverdueAndSuspendUseCase flagOverdueAndSuspendUseCase;
    private final AdvisoryLock advisoryLock;

    @Scheduled(cron = "0 0 6 * * *", zone = "Europe/Istanbul")
    @Transactional
    public void runDailyBilling() {
        if (!advisoryLock.tryAcquire(PLATFORM_BILLING_LOCK_KEY)) {
            log.info("Platform faturalama kilidi baska bir instance'da -- atlaniyor");
            return;
        }

        LocalDate today = LocalDate.now(ISTANBUL);

        try {
            generateDueInvoicesUseCase.execute(today);
        } catch (Exception e) {
            log.error("Fatura uretimi basarisiz: today={}", today, e);
        }
        try {
            remindDueSoonInvoicesUseCase.execute(today);
        } catch (Exception e) {
            log.error("Son-gun hatirlatmasi basarisiz: today={}", today, e);
        }
        try {
            flagOverdueAndSuspendUseCase.execute(today);
        } catch (Exception e) {
            log.error("Gecikme/askiya alma basarisiz: today={}", today, e);
        }
    }
}
```

- [ ] **Step 2: Testini yaz**

`backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/scheduling/PlatformBillingSchedulerTest.java`:
```java
package com.vetos.modules.platformadmin.infrastructure.scheduling;

import com.vetos.modules.platformadmin.application.FlagOverdueAndSuspendUseCase;
import com.vetos.modules.platformadmin.application.GenerateDueInvoicesUseCase;
import com.vetos.modules.platformadmin.application.RemindDueSoonInvoicesUseCase;
import com.vetos.platform.concurrency.AdvisoryLock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlatformBillingSchedulerTest {

    @Mock private GenerateDueInvoicesUseCase generateDueInvoicesUseCase;
    @Mock private RemindDueSoonInvoicesUseCase remindDueSoonInvoicesUseCase;
    @Mock private FlagOverdueAndSuspendUseCase flagOverdueAndSuspendUseCase;
    @Mock private AdvisoryLock advisoryLock;

    @Test
    void should_skipEntirely_when_lockNotAcquired() {
        PlatformBillingScheduler scheduler = new PlatformBillingScheduler(
            generateDueInvoicesUseCase, remindDueSoonInvoicesUseCase, flagOverdueAndSuspendUseCase, advisoryLock
        );
        when(advisoryLock.tryAcquire(anyLong())).thenReturn(false);

        scheduler.runDailyBilling();

        verifyNoInteractions(generateDueInvoicesUseCase, remindDueSoonInvoicesUseCase, flagOverdueAndSuspendUseCase);
    }

    @Test
    void should_runAllThreeSteps_when_lockAcquired() {
        PlatformBillingScheduler scheduler = new PlatformBillingScheduler(
            generateDueInvoicesUseCase, remindDueSoonInvoicesUseCase, flagOverdueAndSuspendUseCase, advisoryLock
        );
        when(advisoryLock.tryAcquire(anyLong())).thenReturn(true);

        scheduler.runDailyBilling();

        verify(generateDueInvoicesUseCase).execute(any());
        verify(remindDueSoonInvoicesUseCase).execute(any());
        verify(flagOverdueAndSuspendUseCase).execute(any());
    }
}
```
(`anyLong` importu: `import static org.mockito.ArgumentMatchers.anyLong;` — dosyanın tepesine ekle.)

- [ ] **Step 3: Tüm backend paketinin geçtiğini doğrula**

Run: `cd backend && ./mvnw -q test`
Expected: PASS, `ApplicationModulesTest` dahil.

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/scheduling/PlatformBillingScheduler.java \
  backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/scheduling/PlatformBillingSchedulerTest.java
git commit -m "feat: PlatformBillingScheduler'a advisory lock ekle"
```

---

## Self-Review Notları (plan yazarı tarafından, kaydedilmeden önce)

- **Spec kapsaması:** Bildirim spec'inin §3 (thread havuzu) → Task 1; §4 (retry+backoff) → Task 2-3; §5 (advisory lock) → Task 1. Genelleme spec'inin §3.1 (Tarbil retry) → Task 4-5; §3.2 (e-Fatura retry, PROCESSING hariç) → Task 7-8; §3.3 (uzlaştırma) → Task 10; §4 (manuel retry tenant kontrolü) → Task 6, 9; §5 (PlatformBillingScheduler lock) → Task 11. Tüm bölümler kapsandı.
- **Sıralama bağımlılığı:** Task 4'ün migration'ı (`V53`) `V36__patients_tenant_id.sql`'den (Kiracı İzolasyonu, zaten `main`'de) sonra numaralandırıldı ve `patients.tenant_id`'ye bağımlı — doğrulandı, `main` şu an `a63e014`'te ve bu kolon zaten var.
- **Tip/imza tutarlılığı:** `markFailed(Instant)` (Notification/Tarbil) vs `markFailed(String, Instant)` (e-Fatura, çünkü zaten bir `reason` parametresi alıyordu) — bilinçli fark, her görevde doğru şekilde kullanıldı. `RetryXUseCase.execute(UUID tenantId, UUID id)` imzası üç modülde de aynı.
- **`TarbilSyncLog`/`NotificationLog` @TenantId dışı kalma kararı:** açıkça Global Constraints'te ve Task 4/6'da gerekçelendirildi, Kiracı İzolasyonu turunun kararıyla tutarlı.
