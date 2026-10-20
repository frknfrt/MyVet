# Teknik Altyapı Yol Haritası (Non-functional)

**Amaç:** 10 başlıktaki (health/metrics, observability, rate limiting, API versioning, logging, caching, SSE, feature management, exception handling, resilience) eksikleri risk önceliğine göre kapatmak.

**Kaynak:** 2026-10-12 tarihli analiz raporu (sohbette). Puanlar o rapordan:
health 2/5 · observability 1.5/5 · rate limit 3/5 · versioning 3/5 · logging 2.5/5 · caching 0/5 · SSE 0/5 · feature mgmt 1.5/5 · exception 4/5 · resilience 2.5/5.

**Çalışma kuralı:** Her faz kendi planıyla (`docs/superpowers/plans/`) ilerler; faz bitmeden sonraki fazın planı yazılmaz (önceki fazın öğrendikleri sonrakini değiştirir). Doğrudan yerel `main`, push yalnız istek üzerine.

---

## Faz 1 — Hızlı risk kapatma (platform, küçük, bağımsız)  ✅ tamamlandı (2026-10-12)
Plan: `2026-10-12-faz1-risk-kapatma.md`

| # | İş | Neden önce | Başlık |
|---|---|---|---|
| 1.1 | Meta/İleti Merkezi/iyzico `RestClient`'larına bağlantı+okuma zaman aşımı; ortak `OutboundHttp` fabrikası; iyzico'ya ulaşılamazsa 500 yerine `PAYMENT_GATEWAY_ERROR` | Cevap vermeyen sağlayıcı thread'i süresiz kilitler — ödeme akışında prod riski | Resilience |
| 1.2 | `GlobalExceptionHandler`'a eksik handler'lar: eksik parametre/yanlış tip (400), yanlış metot (405), yanlış content-type (415), büyük dosya (413), olmayan yol (404) | Bunlar bugün 500 + ERROR log üretiyor: istemci hatası sunucu hatası gibi görünüyor, alarmları kirletiyor | Exception |
| 1.3 | requestId uçtan uca: gelen `X-Request-Id`'yi güvenli biçimdeyse kabul, yanıt header'ına yaz, `ErrorResponse.requestId` alanı, CORS expose | Kullanıcının gördüğü hatayı loga bağlamanın tek yolu | Observability, Exception |
| 1.4 | `@Async` executor'a MDC taşıyan `TaskDecorator`; graceful shutdown | Outbox loglarında requestId/tenantId kayboluyor; deploy'da yarım kalan gönderimler | Observability, Resilience |

**Faz 1'den ertelenenler (final review, minor):** platform-admin CORS'ta `X-Request-Id`/`Location` expose; okuma zaman aşımı soket-başına (toplam süre sınırı Faz 5 TimeLimiter); nginx `client_max_body_size` kontrolü (1 MB varsayılanı 15 MB yüklemeleri Spring'den önce kesebilir); kapanış süresi yorumu (web drain 30 sn + executor 30 sn) ve executor/DataSource yok edilme sırası; unsafe requestId testinde yanıt header'ı doğrulaması.

**Çıkış kriteri:** Tüm birim testleri yeşil, `./mvnw test` yeşil, elle: geçersiz UUID'li bir GET 400 döner ve yanıtta `requestId` + `X-Request-Id` header'ı aynıdır.

## Faz 2 — Plan özelliklerinin (feature flag) gerçekten uygulanması  ✅ kod tamamlandı (2026-10-12) — devreye alma: spec §7 (`docs/superpowers/specs/2026-10-12-plan-ozellik-kisitlamasi-design.md`)
**Ön koşul: tasarım kararı (brainstorming).** `PlanFeatureFlag` bugün sadece admin panelinde; modüllerde engelleme yok.

Karar verilecekler:
- Flag'ler nereden okunacak: JWT claim'i (hızlı, ama plan değişince token yenilenene kadar eski kalır) mı, istek başına `tenant → plan → flags` sorgusu + kısa ömürlü cache (Faz 6'nın ilk kullanıcısı) mı?
- Modulith sınırı: modüller `platformadmin`'e doğrudan bağımlı olamaz → `platform` altında bir `FeatureAccessPort` + platformadmin'de adapter.
- Engelleme noktası: `@RequiresFeature(AI_ASSISTANT)` anotasyonu + interceptor/aspect; hata `FeatureNotInPlanForbiddenException` → 403 `FEATURE_NOT_IN_PLAN`.
- Frontend: `/api/v1/me` (veya mevcut profil ucu) `enabledFeatures` döner; menü öğeleri gizlenir.
- Mevcut kiracılar: migration ile tüm mevcut planlara bugünkü kullanımı bozmayacak flag seti.

Kapsam: AI (SOAP/teşhis/tedavi), E_FATURA, IMAGING, LAB_INTEGRATION, BOARDING, INVENTORY uçları.

## Faz 3 — Gözlemlenebilirlik ve loglama  ✅ kod tamamlandı (2026-10-14) — devreye alma: spec §7 (`docs/superpowers/specs/2026-10-14-gozlemlenebilirlik-faz3-design.md`)
- **3.1 Health:** dış bağımlılık `HealthIndicator`'ları (Ollama/Claude, FaturaEntegratör, iyzico yapılandırıldıysa); `management.endpoint.health.probes.enabled=true` → `/actuator/health/liveness|readiness`; dış servisler readiness'a DEĞİL ayrı bir `external` grubuna (iyzico düştü diye uygulama trafikten çekilmemeli).
- **3.2 Metrik erişimi:** Prometheus scrape için ayrı kimlik (ör. ayrı port `management.server.port` + sadece localhost bind, VPS'te nginx'ten dışarı açılmaz) — `denyAll()` kaldırılır.
- **3.3 İş metrikleri:** `MeterRegistry` ile sayaçlar — e-fatura gönderim sonucu, bildirim gönderim sonucu (kanal etiketli), TARBİL senkron hatası, AI çağrı süresi (`Timer`), outbox kuyruk derinliği (`Gauge`). Etiketlerde tenantId YOK (kardinalite).
- **3.4 Yapısal log:** `logback-spring.xml` — dev'de mevcut düz desen, `prod` profilinde JSON (Spring Boot 3.4+ yerleşik `logging.structured.format.console=ecs`, ek bağımlılık gerekmez), dosya rotasyonu VPS'teki systemd/journald ile.
- **3.5 PII maskeleme:** TCKN/telefon/e-posta loglanan yerlerin taranması, `LogMasking.phone()` gibi yardımcılar.
- **3.6 Audit log:** → **Faz 3b** (ayrı tasarım).
- **3.7 Tracing:** kapsam dışı bırakıldı (tek uygulama; Faz 1 requestId yeterli).

## Faz 3b — Audit log ✅
- `platform/audit` (boş paket): login, rol değişikliği, fatura iptal, reçete, TARBİL gönderimi için `audit_events` tablosu + `AuditPort`. Ayrı tasarım turu.
- ✅ 2026-10-15: 22 klinik olayı, değiştirilemez audit_events, klinik + admin ekranları, impersonate atfı (spec docs/superpowers/specs/2026-10-15-denetim-kaydi-faz3b-design.md).

## Faz 4 — Rate limiting sertleştirme ✅
- Bucket map'i sınırsız büyüyor → Caffeine (`expireAfterAccess(10m)`, `maximumSize`) ile değiştir.
- 429'da `Retry-After` header'ı; JSON gövdeye requestId.
- Kimliği doğrulanmış trafik: **kiracı başına** limit, önce pahalı uçlar (AI üretimi: ör. kiracı başına dakikada 10), sonra genel tavan.
- Ters proxy: VPS'te nginx varsa `server.forward-headers-strategy=native` + nginx'te `X-Forwarded-For`'u kendisi set etmeli; aksi halde herkes tek IP bucket'ını paylaşır. (Önce VPS nginx konfigürasyonu doğrulanacak.)
- Çok instance'a geçilirse: Bucket4j + Postgres/Redis backend (şimdilik gerek yok, tek instance).
- ✅ 2026-10-15: Caffeine IP önbelleği, 429 `Retry-After` + requestId, kiracı genel tavanı ve AI dakikalık sınırı, kampanya alıcı sınırları; ek olarak paket bazlı aylık AI/mesaj kotaları (spec `docs/superpowers/specs/2026-10-15-rate-limit-kota-faz4-design.md`). Çoklu instance (Redis) hâlâ kapsam dışı.

## Faz 5 — Resilience (Resilience4j) ✅
- ✅ 2026-10-16: devre kesiciler (ai, payment, efatura), AI bulkhead (4 / 10 sn), okuma retry'ları, BackoffSchedule, OutboundHttp tekilleştirme (spec docs/superpowers/specs/2026-10-16-resilience-faz5-design.md).
- `resilience4j-spring-boot3` bağımlılığı.
- Circuit breaker: AI adaptörleri (Ollama düşükken her istek 120 sn thread tutmasın), iyzico initialize, FaturaEntegratör.
- Bulkhead: AI çağrıları için eşzamanlılık tavanı (ör. 4).
- Senkron kısa retry: sadece idempotent okuma çağrıları (iyzico retrieve, FaturaEntegratör durum sorgusu) — yazma çağrılarında retry YOK (çift fatura/ödeme riski).
- Outbox retry politikasının tekilleştirilmesi: `EInvoiceSubmissionExecutor` ve `NotificationSendExecutor`'daki kopya `RETRY_BACKOFF` → `platform` altında `BackoffSchedule`.
- AI ve FaturaEntegratör adaptörlerinin kendi `SimpleClientHttpRequestFactory` kopyaları Faz 1'in `OutboundHttp` fabrikasına taşınır.

## Faz 6 — Caching ✅
- `spring-boot-starter-cache` + Caffeine, `@EnableCaching`.
- **Kiracı izolasyonu zorunlu:** cache anahtarında tenantId; `TenantAwareKeyGenerator`; test: A kiracısının cache'i B'ye sızmaz.
- Adaylar (önce ölçüm, sonra cache): plan → feature flag (Faz 2), TARBİL hastalık listesi, kiracı ayarları, özet/dashboard uçları (kısa TTL, 30–60 sn).
- Yazma use case'lerinde `@CacheEvict`.
- HTTP: değişmeyen referans uçlarına `Cache-Control: private, max-age=…`; frontend'de istemci cache (React Query) ayrı bir karar.
- ✅ 2026-10-18: kapsam "ölç-düzelt": paket kodu / paket / TARBİL listesi için kiracı-güvenli @Cacheable (commit sonrası temizlik, 5 dk yedek süre); 4 ana sayfa özeti önbelleksiz DB toplama + İstanbul saati (spec docs/superpowers/specs/2026-10-18-onbellek-faz6-design.md). HTTP Cache-Control ve React Query kapsam dışı.

## Faz 7 — API sözleşmesi ve versiyonlama ✅
- `springdoc-openapi-starter-webmvc-ui` → `/v3/api-docs`, prod'da kapalı veya platform admin'e özel.
- Versiyon politikası dokümanı (`docs/api-conventions.md`'ye ek): kırıcı değişiklik tanımı, v2 açma kuralı, `Deprecation`/`Sunset` header'ları, eski sürüm destek süresi.
- Chrome eklentisi uyumluluğu: eklenti her istekte `X-Client-Version` gönderir; backend desteklenmeyen sürüme `426 CLIENT_UPGRADE_REQUIRED` + anlamlı mesaj.
- ✅ 2026-10-18: eklenti `X-Client-Version` + 426 `CLIENT_UPGRADE_REQUIRED` (`VETLY_EXTENSION_MIN_VERSION`), eklenti 0.2.0 Vetly'den indiriliyor (Ayarlar > Entegrasyonlar, sürüm kartı), versiyonlama kuralları `docs/api-conventions.md`'de, springdoc yalnız geliştirmede (spec docs/superpowers/specs/2026-10-18-api-sozlesmesi-faz7-design.md). `Deprecation`/`Sunset` yalnız yazılı kural.

## Faz 8 — Server-Sent Events ← **SIRADAKİ**
- İlk kullanım: AI SOAP üretimi → `SseEmitter` ile token/aşama akışı (bugün 120 sn senkron bekleme).
- İkinci: kiracı bazlı olay kanalı `/api/v1/events/stream` — e-fatura/TARBİL/bildirim durum değişiklikleri (Modulith domain event'lerinden beslenir).
- Dikkat: JWT ile `EventSource` header gönderemez → kısa ömürlü stream token veya `fetch` + ReadableStream; nginx'te `proxy_buffering off`; bağlantı başına thread maliyeti (virtual threads `spring.threads.virtual.enabled=true` değerlendirilir).

---

## Bağımlılık sırası
```
Faz1 ──► Faz3 (requestId/MDC üzerine metrik+log)
   └───► Faz5 (OutboundHttp üzerine CB)
Faz2 ──► Faz6 (flag lookup ilk cache kullanıcısı)
Faz4, Faz7 bağımsız — araya alınabilir
Faz8 en son (Faz3 metrikleri + Faz5 bulkhead'den faydalanır)
```
