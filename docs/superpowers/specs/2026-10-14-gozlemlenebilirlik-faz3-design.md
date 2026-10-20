# Gözlemlenebilirlik ve Loglama (Faz 3) — Tasarım

**Tarih:** 2026-10-14
**Yol haritası:** `docs/superpowers/plans/2026-10-12-teknik-altyapi-yol-haritasi.md` → Faz 3
**Önceki çalışma:** `2026-09-19-gozlemlenebilirlik-temeli-design.md` (requestId/tenantId MDC, prod log seviyesi, Actuator + Prometheus bağımlılığı — hepsi uygulanmış durumda)
**Durum:** Onaylandı (sohbette, bölüm bölüm)

## 1. Amaç

Bugün Actuator ve Prometheus bağımlılığı var ama:
- Dış servislerin (e-fatura, SMS, WhatsApp, AI, iyzico) bozulduğunu **kimse fark etmiyor**; admin panelindeki "Sistem Sağlığı" ekranı ancak bakılınca görülüyor.
- Kodda hiç iş metriği yok; metrik uçları `SecurityConfig`'te `denyAll()` ile kapalı, okunamıyor.
- Loglar sadece düz metin; ileride bir log aracına bağlanmaya hazır değil.
- 12 dosyada ~20 log satırı telefon, e-posta ve dış servis yanıt gövdesini (TCKN içerebilir) açık yazıyor.

Bu iş: dışarıdan ücretsiz bir uptime servisinin (UptimeRobot) yoklayabileceği iki health ucu, az sayıda anlamlı iş metriği, ayarla açılan JSON log ve iki katmanlı kişisel veri maskelemesi getirir.

## 2. Kullanıcı kararları (sohbetten)

| Konu | Karar |
|---|---|
| İzleme yöntemi | Sunucuya izleme yazılımı kurulmaz (VPS 1 CPU / 2 GB). UptimeRobot health uçlarını yoklar, metrikler hazır bekler (elle okunur), loglar `journalctl` ile. |
| Tracing (yol haritası 3.7) | Çıkarıldı — tek uygulama, Faz 1'deki requestId yeterli. |
| Audit log (yol haritası 3.6) | Ayrı faz: **Faz 3b**, kendi tasarımıyla. |
| Log formatı | Varsayılan düz metin; `LOG_FORMAT=ecs` ile Spring Boot yerleşik JSON (ECS). |
| PII maskeleme | İki katman: satır bazında `LogMasking` + koruma testi; ayrıca tüm log mesajlarında 11 haneli sayılar için güvenlik ağı. |
| Dış servis sağlığı | **Pasif**: dış servise test isteği atılmaz; gerçek çağrıların son 30 dk sonucuna bakılır. |
| TARBİL | Health'e girmez (gönderim hekimin tarayıcısında; tek kliniğin TARBİL oturumu düşünce alarm üretmemeli). Sadece metrik. |
| Git | Commit yok; tüm değişiklikler changelist'te kalır, push yok. |

## 3. Kapsam dışı

- Audit log (Faz 3b).
- Tracing / OpenTelemetry.
- Prometheus/Grafana kurulumu, dashboard, metrik tabanlı alarm.
- Hata izleme servisi (Sentry vb.).
- Stack trace içeriğinin maskelenmesi (bkz. 4.6).
- Kiracı bazında metrik (kardinalite; admin paneli zaten kiracı bazında gösteriyor).

## 4. Tasarım

### 4.1 Ortak parça: `com.vetos.platform.observability` (yeni paket, `@NamedInterface("observability")`)

- **`IntegrationOutcomes`** (Spring bean):
  - `void record(String integration, boolean success)` — her dış servis çağrısının sonucunu bildirmek için tek çağrı.
  - Sonuçları entegrasyon başına bellekte, zaman damgalı tutar; 30 dakikadan eski kayıtlar her `record`/okuma sırasında atılır. Bellek sınırı: entegrasyon başına en fazla 1000 kayıt (daha eskisi atılır).
  - Aynı çağrıda `vetly.integration.calls` sayacını artırır (bkz. 4.3). Health ve metrik tek kaynaktan beslenir.
  - Zaman kaynağı enjekte edilebilir `Clock` (test için).
  - `Health health(String integration)` — 4.2'deki kuralı uygular.
- **Entegrasyon adları** sabitler olarak tek yerde: `efatura`, `sms`, `whatsapp`, `ai`, `payment`, `tarbil`.
- **`LogMasking`** (statik yardımcılar, bkz. 4.5).
- Modül bağımlılığı: ilgili modüllerin `package-info.java` `allowedDependencies` listelerine `platform::observability` eklenir (Faz 1'deki `platform::http` örneği gibi).

### 4.2 Health

**Kural (her entegrasyon için):** son 30 dakikada **en az 3 deneme** var ve **hiçbiri başarılı değil** → `DOWN`. Aksi halde (deneme yok, 3'ten az, ya da en az bir başarı) → `UP`. Uygulama yeniden başlayınca bellek sıfırlanır → `UP`'tan başlar (kabul edildi).

`DOWN` olduğunda bir `WARN` logu yazılır (entegrasyon adı, deneme sayısı, son hata zamanı) — durum değiştiğinde bir kez, her yoklamada tekrar değil.

**HealthIndicator'lar:** `platform/observability` içinde, `efatura`, `sms`, `whatsapp`, `ai`, `payment` adlarıyla birer `HealthIndicator` bean'i (hepsi `IntegrationOutcomes.health(ad)`'a delege eder). `tarbil` için indicator yok.

**Gruplar (`application.yml`):**
```yaml
management:
  endpoint:
    health:
      show-details: never
      probes:
        enabled: true          # /actuator/health/liveness ve /readiness
      group:
        readiness:
          include: readinessState,db
        external:
          include: efatura,sms,whatsapp,ai,payment
```
Dış servisler `readiness`'a bilinçli olarak girmez (iyzico düştü diye uygulama "hazır değil" sayılmamalı).

**Ayrı yönetim portu:**
```yaml
management:
  server:
    port: ${MANAGEMENT_PORT:8081}
    address: 127.0.0.1
```
- Tüm `/actuator/**` uçları ana porttan (8080) kalkar, sadece `127.0.0.1:8081`'de çalışır.
- 8081'de kimlik doğrulama yok; güvenlik ağ seviyesinde (sadece localhost'a bağlı, ufw zaten 22/80/443 dışını kapatıyor). Yönetim bağlamına Spring Security uygulanıyorsa, 8081 için `/actuator/**` herkese açık olacak şekilde ayarlanır — planda testle doğrulanır.
- `SecurityConfig`'teki `/actuator/health` `permitAll` ve `/actuator/prometheus`, `/actuator/metrics/**` `denyAll` kuralları kaldırılır (artık ana portta yoklar).
- Testlerde `management.server.port=0` (rastgele port; yerelde çalışan backend ile çakışmasın).

**Dışarıya açılış (nginx, sadece iki adres):**
```nginx
location = /health/ready    { proxy_pass http://127.0.0.1:8081/actuator/health/readiness; }
location = /health/external { proxy_pass http://127.0.0.1:8081/actuator/health/external; }
```
Yanıtta sadece `{"status":"UP"}` / `{"status":"DOWN"}` (HTTP 200 / 503). Ayrıntı yok; ayrıntı admin panelinde ve logda.

### 4.3 Metrikler

| Metrik | Tür | Etiketler | Kaynak |
|---|---|---|---|
| `vetly.integration.calls` | Counter | `integration` (efatura, sms, whatsapp, ai, payment, tarbil), `outcome` (`success`/`failure`) | `IntegrationOutcomes.record` |
| `vetly.ai.duration` | Timer | `task` (`soap`, `diagnosis`, `treatment`), `provider` (`ollama`/`claude`) | AI adaptörleri |
| `vetly.queue.pending` | Gauge | `queue` (`notification`, `efatura`, `tarbil`) | İlgili modülün kendi deposu, okunduğu anda sayım. efatura: `PENDING`+`PROCESSING`; tarbil: hekim onayı bekleyen (`PENDING`) |
| `vetly.queue.failed` | Gauge | `queue` (`notification`, `efatura`) | Aynı (`FAILED`) |

- Etiketlerde **tenantId yok**.
- Ortak etiket: `management.metrics.tags.application: vetly`.
- Spring'in hazır metrikleri (HTTP, JVM, Hikari) olduğu gibi kalır.
- Gauge'lar her modülün kendi `infrastructure` katmanında, kendi repository'sinde `countByStatus` ile kayıt edilir (modül sınırı korunur).
- Okuma: sunucuda `curl -s localhost:8081/actuator/prometheus | grep vetly_`.

**Kayıt noktaları (`record` çağrıları başarı ve hata yollarına):**
- `efatura`: `FaturaEntegratorEInvoiceGatewayAdapter` (gerçek çağrı); mock adaptör kaydetmez.
- `sms`: `NotificationProviderAdapter` (SMS kanalı) ve `IletiMerkeziPlatformBillingSmsAdapter` — simüle moddayken kaydetmez.
- `whatsapp`: `NotificationProviderAdapter` (WhatsApp kanalı) — simüle moddayken kaydetmez.
- `ai`: Ollama/Claude adaptörleri (6 sınıf); `vetly.ai.duration` de burada. Mock/simüle modda kaydetmez.
- `payment`: `IyzicoPaymentGatewayAdapter` (gerçek çağrı).
- `tarbil`: **sayaç yok** (plan aşamasında netleşti): eklenti hata bildirmiyor, kayıt ya `SUBMITTED` ya `DISMISSED` oluyor; başarısızlık sinyali olmayan bir sayaç anlamsız. Yerine `vetly.queue.pending{queue=tarbil}` (onay bekleyen birikim).

"İş mantığı hatası" ile "servis erişilemiyor" ayrımı (`IntegrationOutcomes.isOutageStatus`): **`failure`** = 5xx, 401/403 (kimlik/yetki — bizim yapılandırmamız bozuk, kullanıcılar etkilenir), 402 (bakiye), 429 (kota), bağlantı/zaman aşımı hatası, boş/beklenmeyen yanıt. **`success`** = diğer 4xx (400, 404, 409, 422 — tek isteğin doğrulama hatası; servis ayakta) ve iş seviyesinde ret içeren 200 yanıtları (ör. iyzico `status=failure`). İleti Merkezi'nin gövdede döndüğü durum kodu da aynı kurala tabidir.

### 4.4 Log formatı

- Varsayılan: bugünkü düz metin (`logging.pattern.correlation` ile requestId/tenantId).
- Ortam değişkeni: **`LOG_FORMAT`**. Değer `ecs` → Spring Boot 3.5 yerleşik JSON (ECS biçimi). Tanımsız/boş → düz metin. (Değer olarak `json` yerine Spring'in biçim adı `ecs` kullanılır; böylece ara eşleme kodu gerekmez.)
- Bağlama: `application.yml`'de `logging.structured.format.console: ${LOG_FORMAT:}`. Boş değerin düz metne düştüğü planda testle doğrulanır; doğrulanamazsa aynı `LOG_FORMAT` değişkeni bir `logback-spring.xml` `<springProperty>` koşuluyla okunur — dışarıdan görünen ayar değişmez.
- MDC alanları (`requestId`, `tenantId`) JSON'da ayrı alan olarak çıkar.

### 4.5 Kişisel veri maskeleme

**1. katman — `LogMasking`:**
- `phone(String)` → ilk 3 ve son 2 rakam açık, arası `*`: `05321234567` → `053******67`. 5 karakterden kısa → `***`. `null` → `null`.
- `email(String)` → yerel kısmın ilk harfi açık: `furkan@gmail.com` → `f*****@gmail.com`. `@` yoksa → `***`.
- `body(String)` → ilk 300 karakter; içindeki 11 haneli sayılar maskelenir (2. katmanla aynı fonksiyon); fazlası `…(+N karakter)`.
- Taramada bulunan dosyalardaki log satırları bu yardımcılarla düzeltilir: `EInvoiceSubmissionExecutor`, `FaturaEntegratorEInvoiceGatewayAdapter`, `NotificationSendExecutor`, `NotificationProviderAdapter`, `GenerateDueInvoicesUseCase`, `HandleSignupPaymentCallbackUseCase`, `StartImpersonationUseCase`, `IletiMerkeziPlatformBillingSmsAdapter`, `IyzicoPaymentGatewayAdapter`, `MockPlatformAnnouncementEmailAdapter`, `MockPlatformBillingEmailAdapter`, `MockInviteEmailAdapter`.

**Koruma testi (`LogPiiGuardTest`):** `src/main/java` altındaki `.java` dosyaları satır satır taranır; `log.(trace|debug|info|warn|error)(` içeren bir ifade (çok satırlı çağrılar kapanan `);`'e kadar birleştirilir) aşağıdakilerden birini `LogMasking.` olmadan argüman olarak geçiriyorsa test kırılır:
- `getResponseBodyAsString()`
- `recipientContact`
- adı `phone`/`Phone`/`email`/`Email` içeren bir tanımlayıcı (ör. `recipientPhone`, `ownerEmail`).
Hata mesajı `dosya:satır` listesi verir. Testin kendisi, bilinen kötü ve iyi örnek metinlerle ayrıca sınanır.

**2. katman — güvenlik ağı:**
- Fonksiyon: `(?<!\d)(\d{3})\d{6}(\d{2})(?!\d)` → `$1******$2` (`123******01`). `LogMasking.elevenDigits` önce rakam sayar, ≥ 11 değilse regex çalıştırmaz.
- Düz metin: logback'in yerleşik `%replace(%m){regex, replacement}` dönüştürücüsü, `logging.pattern.console` içinde (ayrı `logback-spring.xml` yok; Spring Boot'un structured log seçimi bozulmasın diye). Bu yolda ön rakam sayımı yok; regex her mesajda çalışır — kısa mesajlarda maliyeti ihmal edilebilir.
- JSON: `StructuredLoggingJsonMembersCustomizer` ile `message` alanı aynı fonksiyondan geçirilir.
- Dokunulmayanlar: 10 ve 12+ haneli sayılar, harfle başlayan fatura numaraları (`ABC2026000000001`), UUID'ler.

### 4.6 Bilinen sınırlar

- Stack trace içeriği maskelenmez (hata ayıklamayı bozmamak için). 1. katman ve koruma testi, yanıt gövdelerinin hata mesajlarına konmasını engeller.
- Pasif health, o 30 dakikada hiç kullanılmayan bir servisin çöktüğünü fark etmez (kimse etkilenmemiştir).
- Bellekteki sonuçlar yeniden başlatmada kaybolur.

## 5. Hata ve kenar durumları

| Durum | Davranış |
|---|---|
| Mock/simüle adaptör | `record` çağrılmaz → hiç deneme yok → `UP`. |
| 2 hata + 1 başarı (30 dk) | `UP` |
| 3 hata, 0 başarı | `DOWN`, tek seferlik WARN logu |
| Sonra 1 başarı | `UP`, tek seferlik INFO logu ("düzeldi") |
| Sağlayıcı 4xx doğrulama hatası | `success` sayılır (servis ayakta) |
| Gauge sorgusu sırasında DB hatası | Gauge `NaN` döner; health'i etkilemez (`db` göstergesi ayrı) |
| `LOG_FORMAT` tanımsız/boş | Düz metin |
| 8080'de `/actuator/**` | 404 (artık orada yok) |

## 6. Test

- `IntegrationOutcomesTest`: pencere (30 dk) ve eski kaydın atılması (`Clock` ilerletilerek), 1000 kayıt sınırı, `DOWN`/`UP` kuralları, sayacın doğru etiketlerle artması, durum değişiminde tek log.
- `LogMaskingTest`: telefon, e-posta, gövde kısaltma; kısa/boş/`null`.
- `PiiSafetyNetTest`: TCKN ve telefon maskelenir; 10 ve 12 haneli sayı, fatura numarası, UUID dokunulmaz; düz metin ve JSON çıktısında.
- `LogPiiGuardTest`: kaynak taraması + kendi doğruluk testleri (kötü örnek yakalanır, `LogMasking` ile sarılmış örnek geçer).
- Adaptör testleri: başarı/erişilemiyor/4xx yollarında doğru `record` çağrısı (mevcut adaptör testleri genişletilir).
- Kablolama testi (`@SpringBootTest`, `management.server.port=0`): yönetim portunda `/actuator/health/readiness`, `/actuator/health/external`, `/actuator/prometheus` (içinde `vetly_` metrikleri) çalışır; ana portta `/actuator/prometheus` 404.
- `ApplicationModulesTest`: yeni paket ve bağımlılık izinleri modül sınırlarını bozmaz.
- Tam backend test takımı yeşil.

## 7. Devreye alma

1. Kod canlıya çıkar. `application-prod.yml`'de ek ortam değişkeni gerekmez (port varsayılanı 8081).
2. Sunucuda doğrulama: `curl -s localhost:8081/actuator/health/readiness` → `UP`; `curl -s localhost:8081/actuator/prometheus | grep vetly_`.
3. Nginx `vetly` sitesine 4.2'deki iki `location` eklenir; `nginx -t && systemctl reload nginx`.
4. Dışarıdan doğrulama: `https://uygulama.vetly.com.tr/health/ready` ve `/health/external` → `{"status":"UP"}`.
5. UptimeRobot'ta iki HTTP(S) monitörü (5 dk aralık, ücretsiz plan): `/health/ready` ve `/health/external`; bildirim e-postası. Monitör tipi **HTTP(s)**, metod **GET veya HEAD** (ikisi de çalışır; ücretsiz planda varsayılan HEAD'dir).
6. İsteğe bağlı: JSON log istenirse `/etc/vetly/backend.env`'e `LOG_FORMAT=ecs` + `systemctl restart vetly-backend`.
