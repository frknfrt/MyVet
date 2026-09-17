# Bildirim Teslim Sertleştirme (SMS/WhatsApp Prodüksiyon Mimarisi) — Tasarım Dokümanı

**Tarih:** 2026-09-17
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modül:** `modules/notification`

## 1. Bağlam ve Amaç

Proje bir SaaS ve prodüksiyona çıkılacak. `notification` modülü (SMS/WhatsApp gönderimi — randevu hatırlatma, kampanya, şablon) şu an bilinçli olarak "Faz 1" seviyesinde: gerçek bir mesaj kuyruğu (RabbitMQ/Kafka) yok, Spring `@Async` + `NotificationLog` outbox tablosu ile "asenkron, tekrar denenebilir" davranışı taklit ediliyor (`NotificationSendExecutor` javadoc'unda bilinçli bir karar olarak belirtilmiş). Bu doküman, canlıya çıkmadan önce kapatılması gereken üç somut riski ele alıyor.

**Karar verilen bağlam (kullanıcıyla netleştirildi):**
- Yakın vadede ciddi tenant hacmi beklenmiyor, ama üst sınır belli değil — ürün tutarsa yüzlerce tenant olabilir. Bu yüzden **bugün gereksiz altyapı kurmuyoruz**, ama **geçiş noktalarını temiz bırakıyoruz** (mevcut `NotificationSendPort`/`QueueNotificationUseCase`/`NotificationSendExecutor` ayrımı zaten bunu sağlıyor — `docs/architecture.md` §3 Open/Closed ile aynı felsefe).
- Backend Render'da (ya da eşdeğeri) zero-downtime deploy stratejisiyle çalışacak kabul ediliyor — yani deploy sırasında kısa süreliğine **2 instance aynı anda** çalışabilir. Tasarım buna göre güvenli olmalı; onlarca eşzamanlı instance için özel bir optimizasyon gerekmiyor.
- Gönderen kimliği (SMS başlığı/Twilio hesabı) **paylaşımlı kalıyor** — bu doküman kapsamında değil, mimaride buna özel bir esneklik bırakılmıyor.

**Bulunan 3 somut risk (mevcut koddan doğrulandı):**
1. `@EnableAsync` için özel bir `ThreadPoolTaskExecutor` tanımlı değil → Spring varsayılanı (`SimpleAsyncTaskExecutor`) **görev başına sınırsız thread** açıyor. Aynı executor'ı `TarbilSyncExecutor` ve `EInvoiceSubmissionExecutor` da paylaşıyor.
2. Başarısız gönderimler için otomatik retry yok — sadece personelin Ayarlar > SMS/WhatsApp > Gönderim Geçmişi'nden manuel "Tekrar Dene"ye basması var.
3. `AppointmentReminderScheduler` (`@Scheduled`, her gün 09:00) kod yorumunda "tek instance'lik MVP dağıtımına yeterli" diye işaretli. `SendAppointmentRemindersUseCase` zaten `existsByRelatedEntityIdAndNotificationType` ile idempotentlik kontrolü yapıyor ama bu, iki instance'ın **aynı anda** kontrol edip ikisinin de "yok" görmesi yarışını (race) tam kapatmıyor.

## 2. Kapsam

**Bu turda yapılacak:**
- Bildirim gönderimi için sınırlı boyutlu, paylaşımlı bir `ThreadPoolTaskExecutor` (aynı zamanda Tarbil ve e-Fatura async executor'larını da kapsar — aynı isimsiz varsayılan executor'ı paylaştıkları için bu istenen, bedelsiz bir yan etki).
- `notification_log` tablosuna `attempt_count` + `next_retry_at` kolonları, üstel geri çekilmeli (exponential backoff) otomatik retry süpürme işi — Postgres `FOR UPDATE SKIP LOCKED` ile çoklu instance'a güvenli.
- `AppointmentReminderScheduler`'ın gövdesini Postgres transaction-scoped advisory lock (`pg_try_advisory_xact_lock`) ile sarmalayan, tekrar kullanılabilir küçük bir yardımcı (`platform/concurrency`).

**Kapsam dışı (bilinçli olarak):**
- **Gerçek mesaj kuyruğu (RabbitMQ/Redis+worker)** — bugünkü/yakın vadeki hacim için gereksiz karmaşıklık; mevcut port/adapter ayrımı bunu ileride ucuz bir geçiş yapar.
- **Kiracı başına gönderen kimliği/kimlik bilgisi** — kullanıcıyla konuşuldu, şimdilik paylaşımlı model sabit kalıyor.
- **Twilio/İleti Merkezi teslim durumu webhook'u** — şu an sadece ilk API yanıtına bakılıyor ("kabul edildi" ≠ "gerçekten teslim oldu"). Ayrı bir konu, bu turda ele alınmıyor.
- **`GetNotificationStatusSummaryUseCase`'in `findByTenantId` + Java'da filtreleme deseni** — Hızlı Satış turunda benzer bir desen bulunmuştu; burada da var ama bu doküman kapsamının dışında, ayrı bir iyileştirme.
- **Kampanya gönderiminin kendisi (`SendCampaignUseCase`)** — yeni executor ve backoff'tan otomatik olarak faydalanacak (aynı `NotificationSendExecutor`'ı kullanıyor), ama akışın kendisinde değişiklik yok.

## 3. Sınırlı Thread Havuzu

`platform/config/AsyncConfig.java` (yeni dosya) — `@Async`'in varsayılan olarak arayacağı `"taskExecutor"` adında bir bean tanımlar:

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

Sayılar (4/10/500) makul başlangıç değerleri — gerçek trafiğe göre ayarlanabilir, kod değişikliği gerektirmiyor (sadece bean tanımı).

## 4. Otomatik Retry + Backoff

### 4.1 Migration (`V36__notification_retry.sql`)

```sql
ALTER TABLE notification_log ADD COLUMN attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE notification_log ADD COLUMN next_retry_at TIMESTAMPTZ;
CREATE INDEX idx_notification_log_due_retry ON notification_log (next_retry_at)
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;
```

### 4.2 `NotificationLog` domain değişiklikleri

```java
// yeni alanlar
@Column(name = "attempt_count", nullable = false)
private int attemptCount;

@Column(name = "next_retry_at")
private Instant nextRetryAt;

// markFailed() degisir -- backoff karari CAGIRANA (NotificationSendExecutor) birakilir,
// entity kendi backoff politikasini bilmez (bkz. reference-module.md ince entity deseni)
public void markFailed(Instant nextRetryAt) {
    this.status = NotificationStatus.FAILED;
    this.attemptedAt = Instant.now();
    this.attemptCount++;
    this.nextRetryAt = nextRetryAt; // null = otomatik retry hakki bitti, sadece manuel
}

// markRetrying() (mevcut, manuel "Tekrar Dene" ve otomatik suprurme ikisi de kullanir)
// next_retry_at'i temizler -- bir sonraki basarisizlikta backoff yeniden hesaplanir
public void markRetrying() {
    this.status = NotificationStatus.PENDING;
    this.attemptedAt = Instant.now();
    this.nextRetryAt = null;
}
```

`markSent()` değişmiyor (zaten `nextRetryAt`'i sıfırlamaya gerek yok, `SENT` durumundaki bir kayıt bir daha sorgulanmayacak).

### 4.3 Backoff politikası — `NotificationSendExecutor` içinde

```java
private static final Duration[] RETRY_BACKOFF = {
    Duration.ofMinutes(2), Duration.ofMinutes(10), Duration.ofHours(1), Duration.ofHours(6)
};
// attemptCount 1..4 -> otomatik retry planlanir; 5. denemede (attemptCount==5) nextRetryAt=null,
// yani en fazla 4 otomatik retry (toplam 5 deneme), sonrasinda sadece manuel "Tekrar Dene".

private Instant computeNextRetryAt(int attemptCountAfterThisFailure) {
    int index = attemptCountAfterThisFailure - 1;
    if (index >= RETRY_BACKOFF.length) return null;
    return Instant.now().plus(RETRY_BACKOFF[index]);
}
```

`attemptSend(logId)` içindeki mevcut `else { notificationLog.markFailed(); ... }` satırı `notificationLog.markFailed(computeNextRetryAt(notificationLog.getAttemptCount() + 1))` şeklinde güncellenir.

### 4.4 Claim sorgusu — `NotificationLogRepository` + JPA adaptör

Port'a (domain) yeni metot:
```java
List<NotificationLog> claimDueForRetry(Instant now, int limit);
```

`NotificationLogJpaRepository`'de native sorgu (bu kod tabanında native `@Query` zaten kullanılan bir desen, örn. `OwnerJpaRepository.findAnonymousPlaceholder`):
```java
@Query(value = """
    SELECT * FROM notification_log
    WHERE status = 'FAILED' AND next_retry_at IS NOT NULL AND next_retry_at <= :now
    ORDER BY next_retry_at
    LIMIT :limit
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
List<NotificationLog> claimDueForRetry(@Param("now") Instant now, @Param("limit") int limit);
```

`FOR UPDATE SKIP LOCKED`, Postgres'in kendi özelliği: iki instance aynı anda çalışsa bile aynı satırı iki kez kilitleyemezler — biri diğerinin kilitlediği satırları atlar, farklı satırlar üzerinde paralel çalışırlar (yarış değil, doğal iş bölüşümü).

### 4.5 Yeni use-case ve zamanlanmış iş

```java
// application/RetryDueNotificationsUseCase.java
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
        // QueueNotificationUseCase ile ayni desen: commit'ten sonra gonder
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

// infrastructure/scheduling/NotificationRetryScheduler.java
@Component
@RequiredArgsConstructor
class NotificationRetryScheduler {
    private final RetryDueNotificationsUseCase retryDueNotificationsUseCase;

    @Scheduled(fixedDelay = 120_000) // 2 dakika
    public void retryDue() {
        int retried = retryDueNotificationsUseCase.execute();
        if (retried > 0) log.info("Otomatik retry kuyruklandi: adet={}", retried);
    }
}
```

Bu iş **claim üzerinden** çalıştığı için (SKIP LOCKED), ayrı bir advisory lock'a ihtiyaç duymaz — çakışma zaten satır seviyesinde çözülüyor. Bölüm 5'teki advisory lock, farklı bir yarış türünü (iş **üretimi**, "bugün için hatırlatma var mı" kontrolü) kapatıyor.

## 5. Çakışmaya Dayanıklı Zamanlanmış İş (Advisory Lock)

Yeniden kullanılabilir yardımcı, `platform/concurrency/AdvisoryLock.java`:

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

`AppointmentReminderScheduler` değişikliği:
```java
private static final long REMINDER_LOCK_KEY = 7_301_001; // sabit, bu is icin ayrilmis rastgele bir anahtar

@Scheduled(cron = "0 0 9 * * *", zone = "Europe/Istanbul")
@Transactional
public void sendTomorrowReminders() {
    if (!advisoryLock.tryAcquire(REMINDER_LOCK_KEY)) {
        log.info("Randevu hatirlatma kilidi baska bir instance'da -- atlaniyor");
        return;
    }
    // ... mevcut govde degismiyor (tenant donguisu, sendAppointmentRemindersUseCase.execute(...))
}
```

Mevcut `existsByRelatedEntityIdAndNotificationType` idempotentlik kontrolü **kaldırılmıyor** — advisory lock birincil savunma, idempotentlik kontrolü ikincil bir güvenlik ağı olarak kalıyor (örn. iş elle tetiklenirse).

**Neden `AdvisoryLock` `platform/concurrency`'de, `notification` modülünde değil:** Bu, herhangi bir gelecekteki `@Scheduled` işin (örn. ileride eklenebilecek başka bir günlük iş) tekrar kullanabileceği, domain'den bağımsız bir altyapı parçası — `docs/architecture.md`'deki `platform/` katmanının tanımına (cross-cutting, hiçbir domain modülüyle ilgilenmez) tam uyuyor.

## 6. Test Stratejisi

- `NotificationLog` domain testi: `markFailed(nextRetryAt)` sonrası `attemptCount`/`status`/`nextRetryAt` doğru mu; `markRetrying()` `nextRetryAt`'i temizliyor mu.
- `NotificationSendExecutor` testi: `computeNextRetryAt` sınır durumları (1. başarısızlık → 2dk, 4. başarısızlık → 6sa, 5. başarısızlık → `null`).
- `RetryDueNotificationsUseCase` testi: mock repository ile — claim edilen kayıtların `markRetrying()` çağrıldığı ve `attemptSend`'in (afterCommit sonrası, gerçek transaction senkronizasyonu olmadan test ortamında doğrudan) çağrıldığı doğrulanır. `NotificationLogJpaRepository.claimDueForRetry`'nin native SQL'i bu kod tabanındaki mevcut `@DataJpaTest` eksikliği kuralına uyar — repository/adaptör katmanı için ayrı test yazılmaz (reference-module.md ile aynı, `GlobalConstraints`).
- `AdvisoryLock` — gerçek bir Postgres bağlantısı gerektirdiği için birim test yerine, `AppointmentReminderScheduler`'ın kilit alamadığında `sendAppointmentRemindersUseCase.execute(...)`'u hiç çağırmadığını doğrulayan bir mock `AdvisoryLock` testi yeterli (Postgres'in `pg_try_advisory_xact_lock`'unun kendisi test edilmez, bu Postgres'in garantisi).
- `AsyncConfig` — bean'in doğru şekilde oluştuğunu doğrulayan bir Spring context testi (opsiyonel, düşük risk); asıl doğrulama mevcut `ApplicationModulesTest`'in hâlâ geçmesi (yeni `platform/config` paketinin modül sınırlarını bozmadığı).

## 7. Açık Sorular

Yok — tasarım kullanıcı onayından geçti.
