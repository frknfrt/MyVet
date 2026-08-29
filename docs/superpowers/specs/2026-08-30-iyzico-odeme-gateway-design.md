# iyzico Ödeme Gateway Entegrasyonu — Tasarım Dokümanı

**Tarih:** 2026-08-30
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modül:** `modules/platformadmin`

## 1. Bağlam ve Amaç

Platform faturalama sistemi (`PlatformInvoice`/`PlatformPayment`, bkz. `2026-08-28-platform-abonelik-faturalama-design.md`) şu an yalnızca manuel banka havalesi akışını destekliyor: kiracı havale yapar, platform admin `RecordPlatformPaymentUseCase` üzerinden elle "ödendi" işaretler. Bu doküman, kiracının kendi faturasını `/ayarlar/abonelik` sayfasından **tek seferlik online kart ödemesi** ile ödeyebilmesini sağlayacak iyzico entegrasyonunu tanımlıyor.

Bu, Vetly platform billing dekompozisyonunun **sub-proje #3**'ü. Sub-proje #1 (Plan kataloğu) ve #5 (tenant fatura görünümü) tamamlandı; sub-proje #2 (vetly.com pazarlama sitesi) ayrı bir statik proje olarak kuruldu. Sub-proje #4 (vetly.com'da ödeme sonrası otomatik yeni tenant oluşturma) bu turun **kapsamı dışında** — bkz. §2.

## 2. Kapsam

**Bu turda yapılacak:**
- iyzico Checkout Form entegrasyonu (hosted ödeme sayfasına tam sayfa yönlendirme — kart verisi hiçbir zaman bizim sunucumuza uğramaz, PCI kapsamı iyzico'da kalır)
- Kiracının **var olan, ISSUED/OVERDUE durumundaki bir `PlatformInvoice`'unu** `/ayarlar/abonelik` sayfasından online ödemesi
- Kimlik bilgisi (`IYZICO_API_KEY`/`IYZICO_SECRET_KEY`) tanımlı değilken tüm gateway çağrılarının simüle edilmesi (`TwilioNotificationAdapter`/`OllamaSoapGenerationAdapter` ile aynı desen) — yerelde ve CI'da gerçek iyzico hesabı olmadan uçtan uca test edilebilir
- Genel/yeniden kullanılabilir bir `PaymentGatewayPort` — sub-proje #4 geldiğinde sadece yeni bir "initiate" ve yeni bir "callback sonrası ne yap" use-case'i eklenecek, gateway adaptörünün kendisi değişmeyecek

**Kapsam dışı (bilinçli olarak):**
- vetly.com'daki yeni-kiracı-oluşturma akışı (sub-proje #4) — farklı bir başlangıç noktası (kimlik doğrulanmamış ziyaretçi, henüz var olmayan bir tenant) ve farklı bir callback-sonrası mantığı (fatura ödendi işaretlemek yerine tenant oluşturmak) gerektiriyor
- Kayıtlı kart / otomatik tekrarlayan tahsilat (subscription/recurring billing) — her fatura ayrı ayrı, elle tetiklenerek ödenir, tıpkı şu anki banka havalesi modelindeki gibi
- Kısmi ödeme, iade (refund) akışı
- 3D Secure'a özel bir UI — iyzico'nun hosted sayfası bunu zaten kendi içinde yönetiyor

## 3. Veri Modeli Değişiklikleri

### 3.1 `PlatformPaymentMethod` (mevcut enum)

`CARD_ONLINE` değeri eklenir: `BANK_TRANSFER`, `CARD`, `OTHER`, `CARD_ONLINE`. Mevcut `CARD` değeri, admin'in tenant'ın kendisine bildirdiği bir kart ödemesini elle kaydettiği (örn. telefonla) durumu temsil etmeye devam eder; `CARD_ONLINE` yalnızca iyzico tarafından doğrulanmış, callback üzerinden otomatik kaydedilen ödemeler için kullanılır — ikisi denetim (audit) amacıyla ayrı tutulur.

### 3.2 `PlatformPayment` (mevcut entity)

`recordedByAdminId` alanı **nullable** olur (`@Column(nullable = false)` → `nullable = true`). Gateway üzerinden gelen ödemelerde bir admin'in elle kaydı yoktur, bu alan `null` kalır. `notes` alanına iyzico'nun ödeme referansı (`paymentId`) otomatik yazılır — audit/destek amaçlı.

### 3.3 Migration

`V28__payment_gateway.sql` — `ALTER TABLE platform_payments ALTER COLUMN recorded_by_admin_id DROP NOT NULL;`

## 4. Akış (Data Flow)

1. Kiracı `/ayarlar/abonelik` sayfasındaki fatura listesinde ISSUED/OVERDUE bir satırda **"Öde"** butonuna basar.
2. Frontend `POST /api/v1/subscriptions/invoices/{invoiceId}/checkout` çağırır (`TenantBillingController`, `hasRole('ADMIN')`, mevcut desende `TenantContext.current()` ile faturanın gerçekten bu kiracıya ait olduğu doğrulanır — ait değilse veya yoksa `PlatformInvoiceNotFoundException`, başka bir kiracının fatura kimliğinin var olduğu sızdırılmaz).
3. Yeni `InitiateInvoiceCheckoutUseCase`: fatura ISSUED/OVERDUE değilse hata; öyleyse `TenantAdminPort.getOverview(tenantId)` ve `findBillingContactEmail(tenantId)` ile alıcı bilgisi toplanır, `PaymentGatewayPort.initializeCheckout(CheckoutRequest)` çağrılır. `conversationId` olarak **invoice id'nin string hali** gönderilir — bu, callback'te hangi faturaya ait olduğumuzu bulmamızı sağlayan tek referans, ayrı bir "bekleyen ödeme" tablosu gerekmez.
4. Dönen `checkoutFormUrl`, `{ checkoutFormUrl }` olarak frontend'e döner. Frontend `window.location.href = checkoutFormUrl` ile tarayıcıyı tam sayfa yönlendirir.
5. Kullanıcı iyzico'nun sayfasında kart bilgisini girer, öder. iyzico, önceden bizim initialize isteğimizde belirttiğimiz `callbackUrl`'e (`POST /api/v1/public/payments/iyzico/callback`, kimlik doğrulaması **gerektirmez** — mevcut `/api/v1/public/**` permitAll kuralına girer) form-encoded `token` alanıyla POST eder.
6. `PublicPaymentCallbackController`, gelen `token`'ı `HandlePaymentCallbackUseCase`'e iletir. Bu use-case `PaymentGatewayPort.retrieveCheckoutResult(token)` çağırır — bu çağrı **bizim kendi secret'ımızla** iyzico'ya yapılır, yani sahte bir callback POST'u (token bilinse bile) gerçek bir ödeme sonucunu taklit edemez; güven kaynağı bu sorgu adımıdır, ayrı bir imza doğrulaması gerekmez.
7. Sonuç başarılıysa: `conversationId`'den invoice id çözülür, mevcut `RecordPlatformPaymentUseCase` **doğrudan yeniden kullanılır** — `method = CARD_ONLINE`, `recordedByAdminId = null`, `paidAt = bugün`, `notes = "iyzico ödeme referansı: {paymentId}"`. Bu, `PlatformInvoice.markPaid()` + `PlatformPayment` kaydı + `TenantBillingReconciler` (billingStatus → ACTIVE, askıdaysa otomatik aktive) zincirini elle-kayıt akışıyla birebir aynı şekilde tetikler.
8. **Idempotency:** `RecordPlatformPaymentUseCase` içindeki `invoice.markPaid()` fatura zaten `PAID` ise `PlatformInvoiceInvalidTransitionException` fırlatır. `HandlePaymentCallbackUseCase` bu spesifik exception'ı yakalayıp no-op olarak ele alır (tekrarlanan/mükerrer callback çağrısı — iyzico'nun kendi retry mekanizması olabilir — 500 patlamaz, sessizce başarı kabul edilir).
9. Sonuç ne olursa olsun, `PublicPaymentCallbackController` tarayıcıyı `${app.frontend-base-url}/ayarlar/abonelik?odeme=basarili` veya `?odeme=hata` adresine **302** ile geri yönlendirir.
10. `SubscriptionPanel.tsx`, URL'deki `odeme` query param'ını okuyup ilgili banner'ı gösterir ve fatura listesini yeniden çeker (güncel `PAID` durumunu görmek için).

## 5. `PaymentGatewayPort` ve Simüle Modu

```java
package com.vetos.modules.platformadmin.domain;

public interface PaymentGatewayPort {
    boolean isConfigured();
    CheckoutSession initializeCheckout(CheckoutRequest request);
    CheckoutResult retrieveCheckoutResult(String token);
}

public record CheckoutRequest(
    String conversationId, BigDecimal amount, String buyerName, String buyerEmail
) {}

public record CheckoutSession(String checkoutFormUrl, String token) {}

public record CheckoutResult(boolean success, String conversationId, String paymentId) {}
```

`IyzicoPaymentGatewayAdapter` (`modules/platformadmin/infrastructure/adapter`), `TwilioNotificationAdapter`'daki desenle birebir aynı: `IYZICO_API_KEY`/`IYZICO_SECRET_KEY` boşsa `isConfigured()` `false` döner ve:
- **`initializeCheckout`** simüle modda gerçek iyzico'ya hiç istek atmaz; `checkoutFormUrl` olarak **doğrudan kendi public callback endpoint'imizin GET karşılığını** döner (`{callback-base-url}/api/v1/public/payments/iyzico/callback?token=SIMULATED-{conversationId}`) — böylece "Öde" butonuna basan geliştirici, tarayıcısında gerçek bir iyzico sayfası görmeden, doğrudan aynı callback kod yolundan geçerek faturanın ödendiğini yerelde uçtan uca görebilir. `PublicPaymentCallbackController` bu yüzden hem `POST` (gerçek iyzico) hem `GET` (simüle modu) kabul eder — ikisi de aynı `HandlePaymentCallbackUseCase`'i çağırır.
- **`retrieveCheckoutResult`** simüle modda, `token` `SIMULATED-` ile başlıyorsa her zaman `success = true` ve `conversationId`'yi token'dan geri çıkararak döner.

Gerçek modda `initializeCheckout`, iyzico'nun `POST /payment/iyzipos/checkoutform/initialize/auth/ecom` uç noktasına çağrı yapar (`callbackUrl` config'den okunur); `retrieveCheckoutResult`, `POST /payment/iyzipos/checkoutform/auth/ecom/detail` uç noktasını çağırır.

**Güvenlik notu:** Simüle modun `GET` callback'i, credential tanımlı olmadığı her ortamda `?token=SIMULATED-{herhangi-bir-fatura-id}` ile **herhangi bir faturayı bedava ödenmiş işaretlemeye** izin verir — bu, geliştirme/CI için bilinçli bir tasarım ama production'da `IYZICO_API_KEY`/`IYZICO_SECRET_KEY` mutlaka set edilmeli (aynı `JWT_SECRET`/`PLATFORM_ADMIN_PASSWORD` için `application.yml`'de zaten var olan operasyonel gereklilik gibi — bkz. mevcut yorum satırları).

## 6. Config

```yaml
payment-gateway:
  iyzico:
    api-key: ${IYZICO_API_KEY:}
    secret-key: ${IYZICO_SECRET_KEY:}
    base-url: ${IYZICO_BASE_URL:https://sandbox-api.iyzipay.com}
    callback-base-url: ${IYZICO_CALLBACK_BASE_URL:http://localhost:8080}
```

`app.frontend-base-url` zaten mevcut (davet e-postası linkleri için) — ödeme sonrası yönlendirmede o kullanılır, yeni bir config eklenmez.

## 7. Application Katmanı (Use Case'ler)

- `InitiateInvoiceCheckoutUseCase(tenantId, invoiceId)` — §4 adım 3, `CheckoutSession` döner
- `HandlePaymentCallbackUseCase(token)` — §4 adım 6-8, mevcut `RecordPlatformPaymentUseCase`'i çağırır, idempotency'i yönetir
- `RecordPlatformPaymentUseCase` — **değişmez**, olduğu gibi yeniden kullanılır (zaten `recordedByAdminId` nullable UUID kabul edebilecek şekilde bir record parametresi)

## 8. API

`TenantBillingController`'a eklenir (`/api/v1/subscriptions`, `hasRole('ADMIN')`):

| Endpoint | Açıklama |
|---|---|
| `POST /invoices/{invoiceId}/checkout` | `{ checkoutFormUrl }` döner |

Yeni `PublicPaymentCallbackController` (`/api/v1/public/payments/iyzico/callback`, kimlik doğrulama yok — mevcut `/api/v1/public/**` permitAll kuralına girer, `SecurityConfig.java` değişmez):

| Endpoint | Açıklama |
|---|---|
| `POST /` | iyzico'nun gerçek callback'i (`token` form param) |
| `GET /` | Sadece simüle modda kullanılır (`?token=SIMULATED-...`) |

İkisi de işlem sonunda `app.frontend-base-url` + `/ayarlar/abonelik?odeme=basarili\|hata` adresine 302 yönlendirir.

## 9. Frontend

**`subscriptionApi.ts`:** `initiateCheckout: (invoiceId: string) => apiClient.post<{ checkoutFormUrl: string }>(...)`.

**`SubscriptionPanel.tsx`:**
- Fatura tablosunda `status === 'ISSUED' || status === 'OVERDUE'` satırlarına "Öde" butonu — tıklanınca `initiateCheckout` çağrılır, dönen `checkoutFormUrl`'e `window.location.href` ile tam sayfa yönlendirilir.
- Sayfa yüklendiğinde URL'deki `?odeme=basarili|hata` query param'ı okunur (varsa) — ilgili başarı/hata banner'ı gösterilir, param URL'den temizlenir (`history.replaceState`), fatura listesi yeniden çekilir.

## 10. Test Stratejisi

- `IyzicoPaymentGatewayAdapter` için birim test: credential tanımsızken `isConfigured() == false`, `initializeCheckout` simüle `checkoutFormUrl` üretir, `retrieveCheckoutResult("SIMULATED-...")` her zaman başarı döner.
- `PlatformPayment` domain testi: `recordedByAdminId = null` ile `record()` çağrısının çalıştığı doğrulanır.
- `HandlePaymentCallbackUseCase` testi (Mockito, port mock'lanır): başarılı sonuç → `RecordPlatformPaymentUseCase` doğru parametrelerle çağrılıyor mu; fatura zaten `PAID` iken tekrar çağrıldığında exception'ın yutulup no-op davrandığı; bilinmeyen `conversationId` için `PlatformInvoiceNotFoundException` fırlatıldığı.
- Yerel çalışan instance'a karşı curl ile uçtan uca (simüle mod): checkout başlat → dönen `checkoutFormUrl`'e GET (simüle callback) → fatura `PAID` oldu mu, `TenantBillingReconciler` ile `billingStatus = ACTIVE` oldu mu doğrula.

## 11. Açık Sorular

Yok — tasarım kullanıcı onayından geçti.
