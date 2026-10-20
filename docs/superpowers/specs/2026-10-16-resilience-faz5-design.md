# Faz 5 — Resilience (tasarım)

**Tarih:** 2026-10-16
**Yol haritası:** `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` §Faz 5
**Durum:** Onaylandı (bölüm bölüm, 2026-10-16)

## 1. Amaç

Dış servisler (Claude, iyzico, FaturaEntegratör) yavaşladığında ya da kesildiğinde uygulamanın geri kalanının etkilenmemesi:

- AI isteği bir sunucu iş parçacığını 2 dakika bekletmesin; Claude çökükken hekim hemen "AI bağlantısı yok" taslağını görsün.
- Aynı anda çok fazla AI isteği sunucuyu kilitlemesin.
- Ödeme/e-fatura sağlayıcısı çökükken kullanıcı hızlı ve anlaşılır bir mesaj alsın, kuyruk iş parçacıkları zaman aşımı beklemesin.
- Yalnızca **okuma** çağrıları kısa tekrar denensin; **yazma** çağrıları asla (çift ödeme / çift fatura).

Canlıda AI sağlayıcısı: **Claude** (kullanıcı teyidi).

## 2. Kapsam dışı

- SMS/WhatsApp (İleti Merkezi, Meta) için devre kesici — zaten kuyruk + tekrar deneme takvimi var.
- Devre açıkken kuyruktaki e-faturanın deneme hakkını korumak (bugünkü kural aynen: 5 denemeden sonra elle "Tekrar Dene").
- Resilience4j anotasyonları (`@CircuitBreaker` vb.), AOP, time limiter, kütüphanenin rate limiter'ı (Faz 4 kendi rate limiter'ını kullanıyor).
- Ön yüz değişikliği (mevcut hata gösterimi yeterli; plan aşamasında doğrulanır).

## 3. Kütüphane

- `io.github.resilience4j:resilience4j-spring-boot3` **2.4.0** (Maven Central'daki son sürüm; Spring Boot BOM'unda yok → sürüm `pom.xml`'de sabit).
- Apache 2.0 lisans. Getirdikleri: resilience4j çekirdek modülleri + `resilience4j-micrometer`; Vavr yok. `slf4j-api` 1.7.30 runtime bildirimi Boot'un 2.x yönetimiyle ezilir — `mvn dependency:tree` ile doğrulanır.
- `spring-boot-starter-aop` EKLENMEZ (anotasyon kullanılmıyor). Kullanım programatik: `CircuitBreakerRegistry`, `BulkheadRegistry`, `RetryRegistry` bean'leri (starter yml'den kurar) üzerinden `executeSupplier` / `executeCallable` ile açık sarma.
- Metrikler starter + Micrometer ile otomatik (`resilience4j_circuitbreaker_state`, `resilience4j_bulkhead_available_concurrent_calls` vb.), Faz 3'ün 8081 Prometheus ucunda.

## 4. AI koruması

### 4.1 Eşzamanlılık sınırı (bulkhead `ai`)

- Yer: AI use case'leri (`GenerateSoapDraftUseCase`, `GenerateDiagnosisSuggestionUseCase`, `GenerateTreatmentRecommendationUseCase`), **adaptör çağrısını saran** blok. Üç işlev tek havuzu paylaşır.
- `maxConcurrentCalls: 4`, `maxWaitDuration: 10s`.
- Sıra (Faz 4 kotasıyla birlikte): doğrulama → **bulkhead izni** → kota `reserve` → model çağrısı → (hata / `modelConnected=false` ise `release`) → bulkhead izni bırakılır. Yani yer yoksa kota hiç ayrılmaz ve model çağrılmaz.
- Yer açılmazsa: `AiBusyServiceUnavailableException` → **HTTP 503**, `errorCode: AI_BUSY`, mesaj **"AI şu an yoğun, lütfen birkaç saniye sonra tekrar deneyin."**
- `GlobalExceptionHandler`: sınıf adı `ServiceUnavailableException` ile bitenler → 503 (yeni sonek kuralı, mevcut `resolveStatus` desenine eklenir).

### 4.2 Devre kesici (`ai`)

- Yer: 6 AI adaptörünün (Claude ×3, Ollama ×3) içinde, **gerçek HTTP çağrısını** saran blok — Faz 3'teki `outcomes.recordAiCall(...)` çağrısının içinde/yanında; adaptörün mevcut `catch` bloğundan ÖNCE. Açık devre istisnası (`CallNotPermittedException`) mevcut `catch`'e düşer → bugünkü boş taslak (`modelConnected=false`) döner → Faz 4 kuralıyla kota iade edilir.
- Ayarlar: `slidingWindowType: COUNT_BASED`, `slidingWindowSize: 10`, `minimumNumberOfCalls: 5`, `failureRateThreshold: 50`, `slowCallDurationThreshold: 30s`, `slowCallRateThreshold: 50`, `waitDurationInOpenState: 30s`, `permittedNumberOfCallsInHalfOpenState: 2`, `automaticTransitionFromOpenToHalfOpenEnabled: false`.
- **Hata sayılanlar** (Faz 3 `IntegrationOutcomes.isOutageStatus` ile aynı sınıflandırma): bağlantı/zaman aşımı hataları, HTTP ≥500, 401, 402, 403, 429. Diğer 4xx (ör. 400) ve yanıt ayrıştırma hataları devreyi **açmaz** (`recordException` predicate'i + `ignoreExceptions`).
- Durum değişikliği log'u: açılınca WARN `"Devre kesici ACIK: {} (sonraki deneme {} sn sonra)"`, kapanınca INFO `"Devre kesici kapandi: {}"` — tek bir `CircuitBreakerStateLogger` registry olay dinleyicisiyle, tüm devre kesiciler için.
- Ollama adaptörleri de aynı `ai` devre kesicisini kullanır (aynı anda yalnızca biri aktif — `ai.provider`).

### 4.3 Zaman aşımı ve HTTP istemcisi

- AI adaptörleri kendi `SimpleClientHttpRequestFactory` kopyalarını bırakır, `OutboundHttp.restClient(connect 5s, read 60s)` kullanır (cevap bekleme 120 → **60 sn**).

## 5. Ödeme (iyzico)

- `initializeCheckout` (yazma): devre kesici `payment` (AI ile aynı ayarlar; yalnızca `slowCallDurationThreshold: 10s`). Açıkken → `PaymentProviderServiceUnavailableException` → **503**, `errorCode: PAYMENT_PROVIDER_UNAVAILABLE`, mesaj **"Ödeme sistemine şu an ulaşılamıyor, lütfen birkaç dakika sonra tekrar deneyin."** Tekrar deneme **YOK**.
- `retrieveCheckoutResult` (okuma, ödeme dönüşünde): devre kesici **YOK** (para çekilmiş olabilir; kısa aksaklıkta vazgeçilmez). Tekrar deneme `payment-read`: `maxAttempts: 3`, bekleme 500 ms, çarpan 2 (0,5 s → 1 s). Yalnızca §4.2'deki "kesinti" sınıfı hatalar tekrar denenir; iş hatası (ör. 400) denenmez.
- Mevcut Faz 3 `outcomes.record(PAYMENT, ...)` kayıtları aynen kalır (her gerçek HTTP denemesi bir kayıt).

## 6. E-fatura (FaturaEntegratör)

- `submit` (yazma, arka plan kuyruğu): devre kesici `efatura` (`slowCallDurationThreshold: 20s`, diğerleri AI ile aynı). Açıkken HTTP yapılmaz; adaptör sonucu mevcut **geçici hata** sonucu olarak döndürür (`EInvoiceSubmissionOutcome`'un bugünkü "bağlantı hatası" yolu) → kuyruk mevcut takvimle yeniden planlar. Tekrar deneme **YOK**.
- `fetchStatus` (okuma): aynı `efatura` devre kesicisi + tekrar deneme `efatura-read` (3 deneme, 0,5 s → 1 s, yalnızca kesinti sınıfı).
- **Plan aşamasında eklendi:** `ApplyEInvoiceCallbackUseCase`, `fetchStatus` başarısız dönerse kaydı kalıcı `FAILED` işaretliyor. Açık devre bu yola düşmemeli (fatura aslında kesilmiş olabilir; elle tekrar gönderim çift fatura demek) → açık devrede `fetchStatus` **pending** ("hala işleniyor") döner, kayıt değişmez. (Son incelemede genişletildi, 2026-10-16: devre kapalıyken 3 deneme de kesinti sınıfı hatayla biterse de **pending** döner; yalnız kesinti dışı hatalar (ör. 404) `failure` döner.)
- Adaptör kendi HTTP istemcisini bırakır → `OutboundHttp.restClient(connect 5s, read 30s)`.
- Açık devre nedeniyle yapılmayan çağrı Faz 3 `IntegrationOutcomes`'a **kaydedilmez** (gerçek deneme değil; pasif sağlık kuralı bozulmasın).

## 7. Ortak tekrar deneme takvimi

- `com.vetos.platform.concurrency.BackoffSchedule` (veya mevcut uygun platform paketi; plan aşamasında `platform::concurrency` named interface'i kontrol edilir): `static final BackoffSchedule OUTBOX = new BackoffSchedule(2m, 10m, 1h, 6h)`; `Instant nextRetryAt(int attemptCountAfterThisFailure, Instant now)` → takvim bitince `null`.
- `EInvoiceSubmissionExecutor` ve `NotificationSendExecutor`'daki kopya `RETRY_BACKOFF` + `computeNextRetryAt` bunu kullanır. Davranış birebir aynı (mevcut testler aynen geçmeli).

## 8. Testler

- **AI bulkhead:** 4 çağrı sürerken 5.'si bekler; bekleme bitince 503 `AI_BUSY` + Türkçe mesaj; bu durumda kota `reserve` ve model çağrısı yok; izin bırakılınca bekleyen girer (testte bekleme 100 ms).
- **Devre kesici (sahte HTTP sunucusu, Faz 3 adaptör testleri deseni):** art arda 5xx → açılır; açıkken HTTP isteği gitmez ve boş taslak döner; yarı açıkta 2 başarı → kapanır; 400'ler devreyi açmaz; durum değişikliği log'u.
- **iyzico:** açık devrede `initializeCheckout` → 503 + mesaj; `retrieveCheckoutResult` iki 5xx sonrası üçüncüde başarılı; `initializeCheckout` hata alınca **tam 1** HTTP isteği (tekrar yok); retrieve 400'de tekrar yok.
- **FaturaEntegratör:** açık devrede `submit` HTTP yapmaz, geçici hata sonucu döner ve kuyruk `nextRetryAt` planlar; `fetchStatus` tekrar denenir; `submit` tekrar denenmez; açık devre `IntegrationOutcomes`'a kayıt düşmez.
- **`BackoffSchedule`:** 1→+2 dk, 2→+10 dk, 3→+1 sa, 4→+6 sa, 5→`null`; iki kuyruğun mevcut testleri yeşil.
- **Bağlantı (SpringBootTest):** `ai`, `payment`, `efatura` devre kesicileri ve `ai` bulkhead'i yml değerleriyle kurulu; `payment-read`, `efatura-read` retry'ları kurulu; metrikler kayıtlı.
- `mvn dependency:tree`: tek `slf4j-api` sürümü (Boot'unki).
- Tüm backend testleri (793+) yeşil; ön yüz derlemesi temiz; AI ve ödeme ekranlarının 503 mesajını gösterdiği kod okumasıyla doğrulanır.

## 9. Dağıtım

- DB değişikliği yok.
- `docs/deployment.md`: devre kesicilerin ne yaptığı, log'daki "Devre kesici ACIK" uyarısının anlamı, yml ayarları (`resilience4j.*`), metrik adları ve grep örneği.
- Yol haritası: Faz 5 ✅, sıradaki Faz 6.
- Commit/push yok.
