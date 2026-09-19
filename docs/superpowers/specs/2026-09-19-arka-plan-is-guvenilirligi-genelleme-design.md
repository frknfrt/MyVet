# Arka Plan İş Güvenilirliği — Genelleme (Tarbil + e-Fatura) — Tasarım Dokümanı

**Tarih:** 2026-09-19
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modül:** `modules/integration/tarbil`, `modules/integration/efatura`, `modules/platformadmin`

## 1. Bağlam ve Amaç

"Bildirim Teslim Sertleştirme" (`2026-09-17-bildirim-teslim-sertlestirme-design.md`) ve "Kiracı İzolasyonu Sertleştirme" (`2026-09-17-kiraci-izolasyonu-sertlestirme-design.md`) dokümanlarında `notification` modülü için tasarlanan düzeltmelerin **birebir aynı ihtiyacı** iki kardeş sınıfta da doğruladım:

- `TarbilSyncExecutor` ve `EInvoiceSubmissionExecutor`, `NotificationSendExecutor` ile **aynı yorum satırlarına sahip, aynı `@Async` + outbox deseni** — otomatik retry yok, sadece manuel.
- **`POST /tarbil/sync-logs/{id}/retry`** ve **`POST /efatura/submissions/{id}/retry`**, `NotificationLog`'un retry endpoint'inde bulduğumuz **aynı kiracı kontrolsüzlüğüne** sahip — herhangi bir personel, UUID'sini bilirse başka bir kliniğin TARBİL senkronunu veya e-Fatura gönderimini yeniden tetikleyebilir.
- **`PlatformBillingScheduler`** (günde bir, fatura üretimi + son-gün hatırlatma + gecikme/askıya alma), `AppointmentReminderScheduler` ile aynı "tek instance'lik MVP" varsayımını taşıyor, aynı çoklu-instance riski geçerli.

Bu doküman, bu üç deseni **tekrar tasarlamak yerine, zaten onaylanmış çözümleri iki yeni yere uygulamayı** kapsıyor.

**Zaten çözülmüş, bu turda ek iş gerektirmeyen kısım:** Sınırsız thread havuzu sorunu — Bildirim dokümanındaki paylaşımlı `AsyncConfig` bean'i, hiçbir qualifier belirtmeyen tüm `@Async` metotları kapsıyor; `TarbilSyncExecutor.attemptSync` ve `EInvoiceSubmissionExecutor.attemptSubmit` da dahil. O doküman implemente olduğunda bu ikisi otomatik olarak sınırlı havuzu kullanacak.

## 2. Kapsam

**Bu turda yapılacak:**
- `TarbilSyncLog` ve `EInvoiceSubmission` için, Bildirim dokümanındaki `attempt_count`/`next_retry_at` + `FOR UPDATE SKIP LOCKED` süpürme deseninin **aynısı** (§3).
- `RetryTarbilSyncUseCase` ve `RetryEInvoiceSubmissionUseCase`'e, Kiracı İzolasyonu dokümanındaki `RetryNotificationUseCase` düzeltmesiyle **aynı şekilde** tenant kontrolü (§4).
- `PlatformBillingScheduler.runDailyBilling()`'i, Kiracı İzolasyonu dokümanındaki `AdvisoryLock` bileşenini (yeniden kullanarak, yeni bir kilit anahtarıyla) sarmalamak (§5).

**Kapsam dışı (bilinçli olarak):**
- **e-Fatura'nın `PROCESSING` durumunun "sıkışıp kalması"** — sağlayıcıya iletildi ama GIB callback'i hiç gelmezse, bu submission süresiz `PROCESSING`'de kalır; ne otomatik retry (doğru, mükerrer fatura riski) ne de bir zaman aşımı/uzlaştırma (reconciliation) mekanizması var. Bu, retry'dan farklı bir problem (kayıp callback izleme) — ayrı bir tur, bu dokümanın dışında, sadece not düşülüyor.
- **Thread havuzu** — zaten çözülmüş (yukarıda açıklandı), tekrar ele alınmıyor.

## 3. Otomatik Retry + Backoff

Bildirim dokümanının §4'ündeki desenin (migration şekli, backoff sabitleri, `FOR UPDATE SKIP LOCKED` claim sorgusu, `afterCommit` dispatch) **aynısı**, iki yeni yerde:

### 3.1 `TarbilSyncLog`
- Migration: `tarbil_sync_log`'a `attempt_count`/`next_retry_at`.
- `TarbilSyncLog.markFailed(Instant nextRetryAt)` — `NotificationLog.markFailed(Instant)` ile aynı imza/mantık.
- `TarbilSyncLogRepository.claimDueForRetry(Instant now, int limit)` — aynı native `FOR UPDATE SKIP LOCKED` sorgusu, `status = 'FAILED'` filtresiyle.
- `RetryDueTarbilSyncsUseCase` + `TarbilRetryScheduler` (`@Scheduled(fixedDelay = 120_000)`) — `RetryDueNotificationsUseCase`/`NotificationRetryScheduler` ile birebir aynı iskelet.

### 3.2 `EInvoiceSubmission`
- Migration: `e_invoice_submission`'a `attempt_count`/`next_retry_at`.
- **Fark:** claim sorgusu `status = 'FAILED'` filtresini korur — **`PROCESSING` durumundaki kayıtlar asla otomatik süpürmeye girmez** (`RetryEInvoiceSubmissionUseCase`'in zaten `EInvoiceSubmissionAlreadyProcessingException` ile koruduğu aynı kural, otomatik yolda da geçerli — mükerrer GIB gönderimi riskine karşı).
- `RetryDueEInvoiceSubmissionsUseCase` + `EInvoiceRetryScheduler` — aynı iskelet.

Backoff sabitleri (2dk/10dk/1sa/6sa, 5 deneme) Bildirim dokümanıyla **aynı** kalıyor — tutarlılık için, üç modülde farklı sayılar kullanmanın bir gerekçesi yok.

## 4. Manuel Retry Endpoint'lerine Tenant Kontrolü

Kiracı İzolasyonu dokümanının §6'sındaki `RetryNotificationUseCase` düzeltmesinin aynısı:

```java
// RetryTarbilSyncUseCase
@Transactional
public void execute(UUID tenantId, UUID logId) {
    TarbilSyncLog log = tarbilSyncLogRepository.findById(logId)
        .filter(l -> l.getTenantId().equals(tenantId))
        .orElseThrow(() -> new TarbilSyncLogNotFoundException(logId));
    ...
}
```
```java
// RetryEInvoiceSubmissionUseCase — EInvoiceSubmission zaten tenantId'ye sahip
@Transactional
public void execute(UUID tenantId, UUID submissionId) {
    EInvoiceSubmission submission = eInvoiceSubmissionRepository.findById(submissionId)
        .filter(s -> s.getTenantId().equals(tenantId))
        .orElseThrow(() -> new EInvoiceSubmissionNotFoundException(submissionId));
    ...
}
```
Controller'lar `TenantContext.current()`'ı parametre olarak geçirecek şekilde güncellenir (`TarbilController.retry`, `EInvoiceController.retry`).

**Önemli fark — `TarbilSyncLog`'un `tenant_id`'si yok:** Kiracı İzolasyonu dokümanı `TarbilSyncLog`'u bilinçli olarak kapsam dışı bırakmıştı ("ayrı bir soru" notuyla). Bu turda ekleniyor: `tenant_id` kolonu, `patient_id` → `patients.tenant_id` üzerinden geri doldurulur. **Sıralama bağımlılığı:** bu migration, Kiracı İzolasyonu dokümanının Kat 0'ındaki `Patient.tenant_id` migration'ı uygulandıktan **sonra** çalışmalı — implementasyon planında bu iki dokümanın görevleri arasında açık bir sıralama notu olmalı. `EInvoiceSubmission` zaten `tenant_id`'ye sahip, ek migration gerekmiyor.

## 5. `PlatformBillingScheduler` — Advisory Lock

Kiracı İzolasyonu dokümanının §5'indeki `platform/concurrency/AdvisoryLock` bileşeni **yeniden kullanılır** (yeni kod yazılmıyor, sadece enjekte edilip çağrılıyor):

```java
private static final long PLATFORM_BILLING_LOCK_KEY = 7_301_002; // REMINDER_LOCK_KEY'den farkli, ayri bir sabit

@Scheduled(cron = "0 0 6 * * *", zone = "Europe/Istanbul")
@Transactional
public void runDailyBilling() {
    if (!advisoryLock.tryAcquire(PLATFORM_BILLING_LOCK_KEY)) {
        log.info("Platform faturalama kilidi baska bir instance'da -- atlaniyor");
        return;
    }
    // ... mevcut govde degismiyor
}
```

Bu iş `TenantContext`'e ihtiyaç duymuyor (platform admin kapsamında, `PlatformInvoice`/`Subscription` gibi zaten tenant'a özel olmayan ya da `TenantAdminPort` üzerinden erişilen entity'lerle çalışıyor) — Kiracı İzolasyonu dokümanındaki "köprüleme kuralı" burada gerekmez, sadece advisory lock yeterli.

## 6. Test Stratejisi

Üç doğrulanmış desenin (retry+backoff, tenant kontrolü, advisory lock) her biri için zaten Bildirim ve Kiracı İzolasyonu dokümanlarında tanımlanan test şekillerinin **aynısı**, `Tarbil`/`EInvoice`/`PlatformBilling` isimleriyle tekrarlanır:
- `claimDueForRetry` + backoff hesaplama birim testleri (her modül için).
- `RetryTarbilSyncUseCase`/`RetryEInvoiceSubmissionUseCase` — yabancı `tenantId` ile çağrıldığında `NotFoundException` fırlattığını doğrulayan testler.
- `PlatformBillingScheduler` — kilit alınamadığında hiçbir use-case'in çağrılmadığını doğrulayan mock testi.
- **e-Fatura'ya özel ek test:** `claimDueForRetry`'nin `PROCESSING` durumundaki bir kaydı asla döndürmediğini doğrulayan test — bu modülün en kritik davranış garantisi (mükerrer resmi fatura riski).

## 7. Açık Sorular

Yok — tasarım kullanıcı onayından geçti. İmplementasyon sırası: bu doküman, Kiracı İzolasyonu dokümanının `Patient.tenant_id` migration'ından SONRA uygulanmalı (§4'teki sıralama notu).
