# Faz 4 — Rate limiting sertleştirme ve paket kotaları (tasarım)

**Tarih:** 2026-10-15
**Yol haritası:** `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` §Faz 4
**Durum:** Onaylandı (bölüm bölüm, 2026-10-15)

## Amaç

1. Mevcut IP tabanlı korumayı sağlamlaştırmak: sınırlı bellek, `Retry-After`, standart hata gövdesi.
2. Giriş yapmış trafiğe **kiracı başına koruma sınırları** koymak (sunucuyu ve faturayı kaza/kötüye kullanıma karşı korumak).
3. Admin panelindeki paketlerde belirlenen **aylık AI ve mesaj kotalarını** uygulamak (ticari değer).

İki tür sınır bilerek ayrıdır:

| | Koruma sınırı | Paket kotası |
|---|---|---|
| Örnek | AI: kiracı başına dakikada 10 | Pro: ayda 1.000 AI, 5.000 mesaj |
| Nerede ayarlanır | `application.yml` (`vetly.rate-limit.*`), herkese aynı | Admin paneli → Paket Yönetimi (TRIAL: `vetly.quota.trial.*`) |
| Nerede tutulur | Bellek (Bucket4j + Caffeine), yeniden başlatmada sıfırlanır | Postgres (`tenant_monthly_usage`), kalıcı |
| Aşılınca | 429 `RATE_LIMITED` + `Retry-After` | 429 `MONTHLY_QUOTA_EXCEEDED` |

## Kapsam dışı

- Çoklu instance (Bucket4j + Redis/Postgres backend) — tek instance.
- Kullanıcı başına limit; `X-RateLimit-Remaining` gibi bilgi başlıkları.
- CSV dışa aktarım uçlarına özel limit (genel tavan kapsar).
- Kotaların fiyat sayfası kartlarında otomatik gösterimi (pazarlama metni serbest yazılıyor).
- Kota %80 e-posta uyarısı.
- TRIAL kotalarının panelden düzenlenmesi (yml yeterli — kullanıcı kararı).

---

## 1. Koruma sınırları

### 1.1 Ayarlar

```yaml
vetly:
  rate-limit:
    tenant-general-per-minute: 600
    tenant-ai-per-minute: 10
    campaign-recipients-per-hour: 2000
```

Mevcut IP limitleri (auth 5 dk'da 10, platform-admin auth 5 dk'da 10, extension pair 5 dk'da 10, public dakikada 60) **sayı ve davranış olarak aynen kalır**; yml'e taşınmaz (YAGNI).

### 1.2 IP sınırları (`RateLimitFilter`, giriş yapmamış trafik)

- Dört `ConcurrentHashMap<String, Bucket>` → Caffeine `Cache<String, Bucket>`: `expireAfterAccess(10 dk)`, `maximumSize(100_000)`. Yeni bağımlılık: `com.github.ben-manes.caffeine:caffeine` (sürüm Spring Boot BOM'dan).
- Eskimiş "X-Forwarded-For KASITLI OLARAK kullanılmıyor" yorumu güncellenir: prod'da `SERVER_FORWARD_HEADERS_STRATEGY=native` var, Tomcat `RemoteIpValve` güvenilir proxy'den (127.0.0.1) gelen `X-Forwarded-For`'u `getRemoteAddr()`'a yansıtır; nginx header'ı `$remote_addr` ile üzerine yazar. Kod `getRemoteAddr()` kullanmaya devam eder.

### 1.3 Kiracı sınırları (`TenantRateLimitFilter`, yeni)

- Kimlik doğrulamadan **sonra** çalışır; `TenantContext` boşsa (platform admin, anonim) hiçbir şey yapmaz.
- Eklendiği zincirler: ana `SecurityConfig` (`JwtAuthenticationFilter`'dan sonra) ve `TarbilExtensionSecurityConfig` (`ExtensionTokenAuthenticationFilter`'dan sonra). Platform admin zincirine eklenmez.
- **Genel tavan:** her istek kiracının genel havuzundan 1 jeton harcar (`tenant-general-per-minute`).
- **AI üretimi:** `POST /api/v1/ai/soap-drafts`, `POST /api/v1/ai/treatment-recommendations`, `POST /api/v1/ai/diagnosis-suggestions` (tam eşleşme; decode edilmiş, uygulama-göreli path — `RateLimitFilter` ile aynı `UrlPathHelper` yöntemi) ek olarak AI havuzundan 1 jeton harcar (`tenant-ai-per-minute`). `/decision` uçları AI havuzuna dahil **değildir**.
- Önce genel, sonra AI kontrol edilir; genel tavan reddederse AI jetonu harcanmaz.

### 1.4 Ortak kiracı sınırlayıcı (`TenantRateLimiter`)

- Yeni paket `com.vetos.platform.ratelimit` (`@NamedInterface("ratelimit")`): `TenantRateLimiter` bean'i, `RateLimitProperties` (`@ConfigurationProperties("vetly.rate-limit")`), `RateLimitExceededException`.
- Kova anahtarı `havuz + tenantId`; Caffeine `expireAfterAccess(2 saat)`, `maximumSize(50_000)`.
- API: `ConsumeResult tryConsume(Pool pool, UUID tenantId, long tokens)` (başarı veya bekleme süresi), `void refund(Pool pool, UUID tenantId, long tokens)` (Bucket4j `addTokens`).
- `TenantRateLimitFilter` ve kampanya kodu bu bean'i kullanır. Notification modülü `allowedDependencies`'e `platform::ratelimit` eklenir.

### 1.5 Kampanya alıcı sınırları

- `SendCampaignRequest.recipients`: `@Size(max = SendCampaignRequest.MAX_RECIPIENTS)` (`MAX_RECIPIENTS = 1000`, kodda sabit — anotasyon değeri yml'den okunamaz) → fazlası 400 `VALIDATION_FAILED`. Ön yüz de 1.000'den fazla alıcıda göndermeden önce uyarır.
- `SendCampaignUseCase`: önce uygun alıcılar belirlenir (telefonu ve izni olanlar — mevcut atlama kuralları), **uygun alıcı sayısı N** üzerinden:
  1. `TenantRateLimiter.tryConsume(CAMPAIGN_RECIPIENTS, tenant, N)` — yetmezse `RateLimitExceededException` (bekleme süresiyle).
  2. Aylık mesaj kotası ayrılır (§2.4). Kota reddederse saatlik jetonlar `refund` edilir, sonra kota hatası fırlatılır.
  3. Bildirimler kuyruğa alınır. Kuyruğa alma sırasında hata olursa mesaj kotası `release` edilir (saatlik jetonlar iade edilmez — basitlik).
- N = 0 ise hiçbir sınır/kota harcanmaz.

### 1.6 429 yanıtı

- `Retry-After: <saniye>` (yukarı yuvarlanmış, en az 1).
- Gövde `ErrorResponse.of("RATE_LIMITED", mesaj, path)` — mevcut biçim; `requestId` ve `timestamp` dahil (`RequestIdFilter` her iki filtreden önce çalışır).
- Mesaj tam Türkçe cümle, saniyeyi içerir: `"Çok fazla istek gönderildi, lütfen 37 saniye sonra tekrar deneyin."`
- Filtreler yanıtı doğrudan yazar (JSON, `ObjectMapper` ile); kampanya yolu `RateLimitExceededException` fırlatır ve `GlobalExceptionHandler` aynı yanıtı üretir.

---

## 2. Paket kotaları

### 2.1 Veri (migration `V80__plan_quotas_monthly_usage.sql`)

- `plans.monthly_ai_quota INTEGER NULL CHECK (>= 0)`, `plans.monthly_message_quota INTEGER NULL CHECK (>= 0)`. **NULL = sınırsız.** Mevcut paketler NULL başlar → canlıya çıkınca davranış değişmez.
- Yeni tablo:
  ```sql
  CREATE TABLE tenant_monthly_usage (
    tenant_id UUID NOT NULL,
    period    CHAR(7) NOT NULL,      -- '2026-10', Europe/Istanbul takvim ayı
    kind      VARCHAR(16) NOT NULL,  -- 'AI' | 'MESSAGE'
    used      INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (tenant_id, period, kind)
  );
  ```
  Platform düzeyinde tablo (plan/abonelik tabloları gibi); kiracı RLS politikası yok, erişim yalnızca port üzerinden.
- SMS ve WhatsApp **tek "mesaj" kotasını** paylaşır.

### 2.2 Port (`com.vetos.platform.quota`, `@NamedInterface("quota")`)

```java
public enum UsageKind { AI, MESSAGE }

public interface UsageQuotaPort {
    /** Kota yetiyorsa amount kadar ayırır; yetmiyorsa hiçbir şey yazmaz ve QuotaExceededException fırlatır. */
    void reserve(UUID tenantId, UsageKind kind, int amount);
    /** Her zaman sayar, asla engellemez (otomatik mesajlar). */
    void recordUnconditional(UUID tenantId, UsageKind kind, int amount);
    /** Ayrılmış hakkı iade eder (0'ın altına inmez). */
    void release(UUID tenantId, UsageKind kind, int amount);
    /** Bu ayın kullanımı ve kotaları (null kota = sınırsız). */
    UsageSnapshot currentUsage(UUID tenantId);
}
```

- `QuotaExceededException(kind, used, quota, requested)` → `GlobalExceptionHandler` → **429**, `errorCode: MONTHLY_QUOTA_EXCEEDED`, `Retry-After` **yok**.
- Uygulama: `platformadmin` modülünde `PlanUsageQuotaAdapter` (+ `tenant_monthly_usage` için JDBC/native sorgu). `ai` ve `notification` modüllerinin `allowedDependencies`'ine `platform::quota` eklenir; `platformadmin`'e de eklenir. `tenant` modülünün listesi yok (açık) — dokunulmaz.
- **Her port işlemi kendi transaction'ında çalışır (`REQUIRES_NEW`)**: AI ayırması uzun süren model çağrısı boyunca satır kilidi tutmasın, ayrılan hak eşzamanlı isteklerce hemen görülsün. Bu yüzden çağıran işlem sonradan geri alınırsa sayım kendiliğinden geri alınmaz — gerekli yerlerde açık `release` yapılır (§1.5, §2.4); otomatik mesajlarda küçük fazla sayım kabul edilir.

### 2.3 Atomik ayırma

```sql
INSERT INTO tenant_monthly_usage(tenant_id, period, kind, used) VALUES (?, ?, ?, 0)
  ON CONFLICT DO NOTHING;
UPDATE tenant_monthly_usage SET used = used + :n
  WHERE tenant_id = ? AND period = ? AND kind = ? AND used + :n <= :quota;   -- 1 satır = başarılı
```
- Sınırsız kotada (NULL) veya kısıtlama kapalıyken koşulsuz artırılır:
  `INSERT ... VALUES (..., :n) ON CONFLICT (tenant_id, period, kind) DO UPDATE SET used = tenant_monthly_usage.used + :n`.
- İade: `UPDATE ... SET used = GREATEST(used - :n, 0)`.
- Dönem anahtarı `Clock` (Europe/Istanbul) üzerinden hesaplanır — testte saat sabitlenebilir.

### 2.4 Kurallar

- **Kota çözümü** (`PlanFeatureAccessAdapter` ile aynı sıra):
  1. `features.enforcement.enabled=false` → engel yok; **sayım yine yapılır** (koşulsuz artırma). Canlıda bugünkü ayar budur — limit koymadan önce gerçek kullanım görülür.
  2. Abonelik yok → kota 0 + WARN log.
  3. Plan kodu `TRIAL` → `vetly.quota.trial.ai` (varsayılan **30**), `vetly.quota.trial.message` (varsayılan **100**).
  4. Plan bulundu → `monthly_ai_quota` / `monthly_message_quota` (NULL = sınırsız).
  5. Plan kodu var ama plan satırı yok → kota 0 + WARN log.
  Önbellek yok (Faz 2 ile aynı: admin değeri değiştirince sonraki istek görür).
- **AI** (SOAP taslağı, tanı önerisi, tedavi önerisi — üçü de, SOAP dahil): model çağrılmadan önce `reserve(AI, 1)`; model çağrısı istisna fırlatırsa **veya** yanıt `modelConnected() == false` ise (adaptörler hatayı yakalayıp boş taslak döndürüyor) `release(AI, 1)`; istisna aynen yayılır, boş taslak aynen döner. Doğrulama hatası (ör. Assessment boş) `reserve`'den **önce** kontrol edilir — hak harcanmaz. Kota doluysa model **hiç çağrılmaz**. Mesaj: `"Bu ayki AI hakkınız doldu (100/100). Paketinizi yükseltmek için yöneticinize başvurun."`
- **Kampanya:** uygun alıcı sayısı N için `reserve(MESSAGE, N)` — ya hepsi ya hiçbiri. Mesaj: `"Bu ayki mesaj hakkınız yetersiz: 300 alıcı, kalan 120."`
- **Otomatik mesajlar** (`APPOINTMENT_CONFIRMATION`, `APPOINTMENT_REMINDER`, `VACCINATION_REMINDER`): `QueueNotificationUseCase` içinde `CAMPAIGN_MESSAGE` dışındaki her tür için `recordUnconditional(MESSAGE, 1)`. **Asla engellenmez**, kotayı aşabilir.
- Kampanya mesajları `QueueNotificationUseCase`'te tekrar sayılmaz (zaten §1.5'te ayrıldı).
- Mesaj kuyruğa alındığında sayılır; başarısız gönderimde iade yok; tekrar deneme (`/logs/{id}/retry` ve otomatik retry) yeniden sayılmaz.

---

## 3. Ekranlar ve uçlar

### 3.1 Admin — Paket Yönetimi (`PlanManagementPage.tsx`)
- `CreatePlanRequest` / `UpdatePlanRequest` / `PlanResponse`: `monthlyAiQuota`, `monthlyMessageQuota` (`Integer`, null olabilir, `@PositiveOrZero`). `Plan.updateDetails` ve `create` akışı bunları taşır.
- Forma iki sayı alanı: **"Aylık AI hakkı"**, **"Aylık mesaj hakkı (SMS + WhatsApp)"**; boş = sınırsız (yer tutucu metin "Sınırsız").
- Sayfa altında salt-okunur satır: `"Deneme paketi kotaları: 30 AI / 100 mesaj (sunucu ayarı)"` — değerler backend'den gelir (`GET /api/v1/platform-admin/plans/trial-quotas` veya plan listesi yanıtına ek alan; plan aşamasında mevcut uç yapısına göre seçilir).

### 3.2 Admin — Klinik Detayı (`TenantDetailPage.tsx`)
- "Bu ayki kullanım" kutusu: `AI: 34 / 100 · Mesaj: 1.240 / 1.000 — aşım: 240`. Sınırsızsa `34 (sınırsız)`.
- Veri: tenant detay yanıtına ek alan veya `GET /api/v1/platform-admin/tenants/{id}/usage` (plan aşamasında mevcut yapıya göre).

### 3.3 Klinik — Ayarlar → Abonelik (`SubscriptionPanel.tsx`)
- `GET /api/v1/subscriptions/usage` — `@PreAuthorize("hasRole('ADMIN')")`, `SubscriptionsController`'a eklenir, `UsageQuotaPort.currentUsage(TenantContext.current())`.
- İlerleme çubuğuyla `Bu ay AI: 34/100`, `Mesaj: 820/1.000`; sınırsızsa `34 (sınırsız)`.

### 3.4 Hata mesajları
- Ön yüz `ApiError` mesajını gösteriyor; bu yüzden mesajlar arka uçta tam cümle yazılır, yeni hata işleme kodu yazılmaz. Plan aşamasında AI (EncounterPage) ve kampanya ekranlarının sunucu mesajını gerçekten gösterdiği doğrulanır; göstermiyorsa en küçük düzeltme yapılır.

---

## 4. Testler

**Koruma sınırları**
- Mevcut `RateLimitFilterTest`, `RateLimitFilterExtensionPairTest` aynen geçer.
- 429'da `Retry-After` (pozitif tam sayı) ve gövdede `requestId`, `errorCode: RATE_LIMITED`.
- IP önbelleği `maximumSize` aşımında eski girişleri atar (küçük boyutla kurulan test örneği).
- Kiracı genel tavanı; AI dakikalık sınırı; `/decision` uçları AI havuzunu harcamaz; iki kiracı birbirinin hakkını yemez; `TenantContext` yokken filtre dokunmaz; genel tavan reddettiğinde AI jetonu harcanmaz.
- Kampanya: 1.001 alıcı → 400; saatlik alıcı sınırı → 429; kota reddinde saatlik jetonlar iade edilir; N=0 hiçbir şey harcamaz.

**Kotalar** (gerçek Postgres, `@SpringBootTest`)
- Sınıra gelince `reserve` reddedilir ve hiçbir şey yazılmaz.
- **Eşzamanlılık:** kalan hak K iken 20 paralel `reserve(1)` → tam K başarılı, `used == quota`.
- Kampanya ya hepsi ya hiçbiri; AI istisnasında hak iade edilir ve istisna yayılır; kota doluyken model çağrılmaz.
- Otomatik mesaj kota doluyken de kuyruğa alınır ve sayılır; kampanya mesajı iki kez sayılmaz.
- Ay değişimi (sabit `Clock`, 31 Ekim 23:59 İstanbul → 1 Kasım 00:00) yeni satır.
- NULL kota sınırsız; abonelik yok → 0; TRIAL yml değerleri; kısıtlama kapalıyken sayım var engel yok; plan satırı yok → 0.

**Uçlar ve ön yüz**
- Plan oluştur/güncelle yeni alanları kaydeder ve döndürür; negatif değer 400.
- `/subscriptions/usage` yalnızca ADMIN (VET → 403).
- Ön yüz: paket formu alanları (boş → null gönderimi), kullanım kutuları (sınırsız/aşım gösterimi) için mevcut test düzeninde bileşen testleri.

**Bitiş ölçütü:** tüm backend testleri (şu an 645) + frontend testleri yeşil; modulith doğrulama testi yeşil.

## 5. Dağıtım

- `V80` migration otomatik çalışır; mevcut paketler sınırsız başlar.
- `docs/deployment.md`:
  - Nginx kontrolü: `/api` location'ında `proxy_set_header X-Forwarded-For $remote_addr;` olmalı; backend log'unda gerçek istemci IP'si görünmeli (ör. yanlış şifreyle bir giriş deneyip log'a bakmak).
  - Yeni ayarlar: `vetly.rate-limit.*`, `vetly.quota.trial.*` (env ile değiştirilebilir).
  - Öneri: kısıtlama kapalıyken birkaç hafta "Bu ayki kullanım" sayılarını izle, sonra paketlere kota gir ve `FEATURES_ENFORCEMENT_ENABLED=true` yap.
- Yol haritasında Faz 4 ✅.
- Commit/push yok — değişiklikler changelist'te kalır.
