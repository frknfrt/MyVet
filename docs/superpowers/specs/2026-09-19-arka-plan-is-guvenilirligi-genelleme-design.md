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
- `TarbilSyncLog` ve `EInvoiceSubmission` için, Bildirim dokümanındaki `attempt_count`/`next_retry_at` + `FOR UPDATE SKIP LOCKED` süpürme deseninin **aynısı** (§3.1, §3.2).
- Sıkışan `PROCESSING` kayıtları için, mevcut `ApplyEInvoiceCallbackUseCase`'i yeniden kullanan bir uzlaştırma (reconciliation) işi (§3.3).
- `RetryTarbilSyncUseCase` ve `RetryEInvoiceSubmissionUseCase`'e, Kiracı İzolasyonu dokümanındaki `RetryNotificationUseCase` düzeltmesiyle **aynı şekilde** tenant kontrolü (§4).
- `PlatformBillingScheduler.runDailyBilling()`'i, Kiracı İzolasyonu dokümanındaki `AdvisoryLock` bileşenini (yeniden kullanarak, yeni bir kilit anahtarıyla) sarmalamak (§5).

**Kapsam dışı (bilinçli olarak):**
- **Thread havuzu** — zaten çözülmüş (yukarıda açıklandı), tekrar ele alınmıyor.

## 3. Otomatik Retry + Backoff

Bildirim dokümanının §4'ündeki desenin (migration şekli, backoff sabitleri, `FOR UPDATE SKIP LOCKED` claim sorgusu, `afterCommit` dispatch) **aynısı**, iki yeni yerde:

### 3.1 `TarbilSyncLog`
- Migration: `tarbil_sync_log`'a `attempt_count`/`next_retry_at`.
- `TarbilSyncLog.markFailed(Instant nextRetryAt)` — `NotificationLog.markFailed(Instant)` ile aynı imza/mantık.
- `TarbilSyncLogRepository.claimDueForRetry(Instant now, int limit)` — aynı native `FOR UPDATE SKIP LOCKED` sorgusu, `status = 'FAILED'` filtresiyle.
- `RetryDueTarbilSyncsUseCase` + `TarbilRetryScheduler` (`@Scheduled(fixedDelay = 120_000)`) — `RetryDueNotificationsUseCase`/`NotificationRetryScheduler` ile birebir aynı iskelet.

### 3.2 `EInvoiceSubmission` (tablo adı: `efatura_submission`)
- Migration: `efatura_submission`'a `attempt_count`/`next_retry_at`.
- **Fark:** claim sorgusu `status = 'FAILED'` filtresini korur — **`PROCESSING` durumundaki kayıtlar asla otomatik süpürmeye girmez** (`RetryEInvoiceSubmissionUseCase`'in zaten `EInvoiceSubmissionAlreadyProcessingException` ile koruduğu aynı kural, otomatik yolda da geçerli — mükerrer GIB gönderimi riskine karşı).
- `RetryDueEInvoiceSubmissionsUseCase` + `EInvoiceRetryScheduler` — aynı iskelet.

Backoff sabitleri (2dk/10dk/1sa/6sa, 5 deneme) Bildirim dokümanıyla **aynı** kalıyor — tutarlılık için, üç modülde farklı sayılar kullanmanın bir gerekçesi yok.

### 3.3 Sıkışan `PROCESSING` Kayıtları İçin Uzlaştırma

**Problem:** e-Fatura sağlayıcısına (faturaentegrator) iletilen ama GIB resmileşme callback'i hiç gelmeyen bir kayıt, süresiz `PROCESSING`'de kalır. Otomatik retry buna kasıtlı olarak dokunmuyor (§3.2, mükerrer gönderim riski) — ama şu an bunu **çözecek** hiçbir mekanizma da yok, sadece webhook'un gelmesine güveniliyor.

**Çözüm — yeni kod değil, mevcut mekanizmanın tekrar kullanımı:** `EInvoiceGatewayPort.fetchStatus(providerReference)` zaten gerçek adaptörde (`FaturaEntegratorEInvoiceGatewayAdapter`) tam implemente — sağlayıcının `/invoices/{id}` uç noktasını sorguluyor. `ApplyEInvoiceCallbackUseCase` da zaten bunu çağırıp sonucu idempotent şekilde işliyor (zaten `SUBMITTED`/`FAILED` olan kayda dokunmuyor, hâlâ işleniyorsa durumu değiştirmeden bırakıyor). Webhook, bu use-case'i tetikleyen tek yol değil — **aynı use-case'i bir zamanlayıcıdan da çağırabiliriz**, yeni bir çözümleme mantığı yazmadan:

```java
// EInvoiceSubmissionRepository — yeni port metodu
List<String> findProviderReferencesByStatusAndAttemptedAtBefore(EInvoiceSubmissionStatus status, Instant threshold);

// application/ReconcileStuckEInvoiceSubmissionsUseCase.java — yeni, kucuk
@Service
@RequiredArgsConstructor
public class ReconcileStuckEInvoiceSubmissionsUseCase {
    private static final Duration STALE_THRESHOLD = Duration.ofHours(1);

    private final EInvoiceSubmissionRepository eInvoiceSubmissionRepository;
    private final ApplyEInvoiceCallbackUseCase applyEInvoiceCallbackUseCase;

    public int execute() {
        List<String> stale = eInvoiceSubmissionRepository.findProviderReferencesByStatusAndAttemptedAtBefore(
            EInvoiceSubmissionStatus.PROCESSING, Instant.now().minus(STALE_THRESHOLD)
        );
        stale.forEach(applyEInvoiceCallbackUseCase::execute);
        return stale.size();
    }
}

// infrastructure/scheduling/EInvoiceReconciliationScheduler.java
@Scheduled(fixedDelay = 3_600_000) // saatte bir -- FAILED retry'lerden (2dk) cok daha seyrek,
                                    // aciliyeti yok, saglayici API'sini gereksiz yormasin
public void reconcile() {
    int resolved = reconcileStuckEInvoiceSubmissionsUseCase.execute();
    if (resolved > 0) log.info("e-Fatura uzlastirmasi tetiklendi: adet={}", resolved);
}
```

`FOR UPDATE SKIP LOCKED`'a burada gerek yok: `ApplyEInvoiceCallbackUseCase` zaten idempotent (aynı kayda iki instance aynı anda dokunsa bile aynı sonuca varır, zarar yok) — bu, §3.1/§3.2'deki "claim et, hemen PENDING'e çek" deseninden daha basit, çünkü mutasyon zaten güvenli bir use-case'e devrediliyor. `attemptedAt`, `ApplyEInvoiceCallbackUseCase`'in "hâlâ işleniyor" dalında güncellenmiyor — yani gerçekten sıkışmış bir kayıt her saat başı yeniden kontrol edilecek (istenen davranış, sağlayıcıyı yormayacak kadar seyrek).

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
- **e-Fatura'ya özel ek testler:**
  - `claimDueForRetry`'nin `PROCESSING` durumundaki bir kaydı asla döndürmediğini doğrulayan test — bu modülün en kritik davranış garantisi (mükerrer resmi fatura riski).
  - `ReconcileStuckEInvoiceSubmissionsUseCase` — mock `ApplyEInvoiceCallbackUseCase` ile: eşik altındaki (henüz taze) `PROCESSING` kayıtların çağrılmadığını, eşik üstündekilerin `providerReference`'ıyla çağrıldığını doğrulayan birim test.

## 7. Açık Sorular

Yok — tasarım kullanıcı onayından geçti. İmplementasyon sırası: bu doküman, Kiracı İzolasyonu dokümanının `Patient.tenant_id` migration'ından SONRA uygulanmalı (§4'teki sıralama notu).
