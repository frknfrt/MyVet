# vetly.com Ödeme Sonrası Otomatik Tenant Oluşturma — Tasarım Dokümanı

**Tarih:** 2026-08-30
**Durum:** Tasarım onaylandı, implementasyon bekliyor
**İlgili modül:** `modules/platformadmin`, `modules/tenant` (paylaşılan `StaffInvite`), `vetly-site` (ayrı proje)

## 1. Bağlam ve Amaç

Sub-proje #3 (iyzico ödeme gateway entegrasyonu) genel amaçlı, yeniden kullanılabilir bir `PaymentGatewayPort` + `IyzicoPaymentGatewayAdapter` kurdu, ama sadece **mevcut** tenant'ların kendi platform faturalarını ödemesi için kullandı. Bu doküman, Vetly platform billing dekompozisyonunun son parçası olan sub-proje #4'ü tanımlıyor: vetly.com (ayrı statik proje, `C:\Users\Furkan\Desktop\vetly-site`) üzerinde kimlik doğrulanmamış bir ziyaretçinin plan seçip ödeme yapması, ödeme başarılı olunca **otomatik olarak yeni bir klinik/tenant oluşturulması**.

Bu, #3'ten temel bir farkla ayrılıyor: #3'te ödenen şey zaten var olan bir `PlatformInvoice`; burada ödeme anında henüz ne bir tenant ne bir fatura var — ödeme, bir tenant'ın *var olmasının önkoşulu*.

## 2. Kapsam

**Bu turda yapılacak:**
- Yeni `TenantSignupRequest` domain nesnesi (bekleyen kayıt — ödeme başlamadan önce minimal ziyaretçi bilgisini tutar)
- Yeni public uç noktalar: aktif planları listeleme, checkout başlatma
- Mevcut `PublicPaymentCallbackController`'a küçük bir yönlendirme eklenmesi — aynı callback, önce mevcut-fatura-ödemesi (#3) olarak dener, olmazsa yeni-kayıt-ödemesi olarak dener
- `TenantAdminPort`'a yeni bir metod: ödeme sonrası tenant'ı **ACTIVE** (TRIAL değil) planla oluşturan, ama `StaffUser` oluşturmayan bir varyant
- İlk dönem için gerçek bir `PlatformInvoice` + `PlatformPayment` kaydı (audit trail tutarlılığı)
- Yönetici hesabının kurulması için **mevcut `StaffInvite`/`AcceptStaffInviteUseCase`/`/davet/:token` akışının yeniden kullanılması** (`invitedByStaffUserId` nullable yapılarak "sistem daveti" temsil edilir)
- vetly-site'ta: `ContactCta.tsx`'in gerçek bir checkout formuna dönüştürülmesi, `Pricing.tsx`'in gerçek backend plan verisinden beslenmesi, CORS'un vetly.com origin'ine açılması

**Kapsam dışı (bilinçli olarak):**
- Yıllık ödeme seçeneği (sadece aylık)
- vetly-site'ta gerçek zamanlı form-doğrulama ötesinde bir "hesap oluşturma sihirbazı" — form minimal kalır
- Ayrı bir "şifre sıfırlama" özelliği (bu turda ihtiyaç yok — StaffInvite akışı yeterli)
- İkinci bir plan'ın ("Başlangıç") backend'e girilmesi — bu bir **veri girişi** işi (platform admin panelinden), kod kapsamının dışında; implementasyon bittiğinde manuel yapılmalı

## 3. Veri Modeli

### 3.1 `TenantSignupRequest` (yeni entity, `modules/platformadmin/domain`)

| Alan | Tip | Açıklama |
|---|---|---|
| `id` | UUID | PK — iyzico `conversationId` olarak kullanılır |
| `clinicName` | String | Formdan |
| `adminFullName` | String | Formdan |
| `adminEmail` | String | Formdan — ödeme öncesi benzersizlik kontrolü yapılır |
| `phone` | String (nullable) | Formdan, opsiyonel |
| `planCode` | String | Satın alınan plan kodu |
| `status` | enum | `PENDING`, `COMPLETED` |
| `createdAt` | Instant | — |

Domain metodları: `create(clinicName, adminFullName, adminEmail, phone, planCode)` (statik factory, `status=PENDING`), `complete()` (`PENDING → COMPLETED`, zaten `COMPLETED` ise no-op — idempotency için, `PlatformInvoice`'daki gibi exception fırlatmaz, çünkü burada "tekrar dene" senaryosu normal ve beklenen).

### 3.2 `StaffInvite` (mevcut entity, değişiklik)

`invited_by_staff_user_id` kolonu **nullable** olur. `null` = "sistem tarafından, ödeme sonrası otomatik davet" (bir kişi tarafından değil). Bu alan şu an kodun hiçbir yerinde okunmuyor/gösterilmiyor (sadece yazılıyor) — nullable yapmak mevcut hiçbir davranışı bozmaz.

### 3.3 Migration

`V30__signup_requests.sql`:
```sql
CREATE TABLE tenant_signup_requests (
    id               UUID PRIMARY KEY,
    clinic_name      TEXT NOT NULL,
    admin_full_name  TEXT NOT NULL,
    admin_email      TEXT NOT NULL,
    phone            TEXT,
    plan_code        TEXT NOT NULL,
    status           TEXT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL
);

ALTER TABLE staff_invites ALTER COLUMN invited_by_staff_user_id DROP NOT NULL;
```

## 4. Akış (Data Flow)

1. Ziyaretçi vetly.com'da plan seçer (`GET /api/v1/public/signup/plans` ile listelenen aktif planlardan), formu doldurur (klinik adı, yönetici adı-soyadı, e-posta, opsiyonel telefon).
2. Frontend `POST /api/v1/public/signup/checkout` çağırır. Yeni `InitiateSignupCheckoutUseCase`:
   - Plan var mı ve aktif mi kontrol eder (yoksa hata).
   - `tenantAdminPort.isEmailRegistered(adminEmail)` ile e-posta zaten kayıtlı mı kontrol eder; kayıtlıysa **ödeme başlamadan** `SignupEmailAlreadyRegisteredConflictException` (409, bkz. §5) fırlatır.
   - `TenantSignupRequest.create(...)` kaydeder.
   - `paymentGatewayPort.initializeCheckout(conversationId=signupRequest.id.toString(), amount=plan.monthlyPrice, buyerName=adminFullName, buyerEmail=adminEmail)` çağırır.
   - `{ checkoutFormUrl }` döner.
3. Tarayıcı `checkoutFormUrl`'e tam sayfa yönlenir (iyzico veya simüle callback — #3'teki ile birebir aynı mekanizma).
4. Callback `/api/v1/public/payments/iyzico/callback`'e gelir. `PublicPaymentCallbackController.handleAndRedirect` şu sırayla dener (exception-tabanlı fallback — Approach A'nın "sırayla dene" fikri, en basit haliyle):
   a. Önce **mevcut** `handlePaymentCallbackUseCase.execute(token, today)` çağrılır (#3 akışı, **imzası ve iç mantığı hiç değişmez** — kendi `retrieveCheckoutResult` çağrısını kendisi yapar).
   b. Bu çağrı `PlatformInvoiceNotFoundException` fırlatırsa (yani `conversationId` bilinen bir faturaya ait değil) → yeni `handleSignupPaymentCallbackUseCase.execute(token, today)` denenir (aynı imza şekli: `(String token, LocalDate today): boolean`, kendi `retrieveCheckoutResult` çağrısını kendisi yapar).
   c. İkisi de bulamazsa (`TenantSignupRequestNotFoundException`) veya başka bir beklenmeyen hata olursa → `?odeme=hata` (mevcut catch-all davranış, §Finding-3'teki gibi).
   d. Herhangi biri `true`/`false` döndürürse (fatura veya kayıt bulundu, işlendi) → o sonuca göre `?odeme=basarili|hata`.

   Not: bu, `paymentGatewayPort.retrieveCheckoutResult(token)`'ın senaryoya göre bir veya iki kez çağrılmasına yol açabilir (fatura bulunamayınca ikinci deneme kendi sorgusunu tekrar yapar) — bu sorgu salt-okunur ve iyzico tarafında tekrarlanabilir olacak şekilde tasarlanmıştır (simüle modda zaten hiç ağ çağrısı yok), yani fazladan çağrı zararsızdır. Bu yaklaşım, `HandlePaymentCallbackUseCase`'in imzasını değiştirip önceden alınmış bir `CheckoutResult`'ı parametre olarak geçirmekten daha basit ve #3'ün koduna sıfır dokunuşla sonuçlanıyor.
5. `HandleSignupPaymentCallbackUseCase` (yeni):
   - `TenantSignupRequest` zaten `COMPLETED` ise → idempotent no-op, `true` döner (tekrar tenant oluşturmaz).
   - Aksi halde: `tenantAdminPort.createTenantForPaidSignup(...)` çağrılır (bkz. §5) → `(tenantId, branchId)`.
   - `StaffInvite.create(tenantId, branchId, adminEmail, adminFullName, StaffRole.ADMIN, null)` kaydedilir, `InviteEmailPort.sendInvite(...)` ile "hesabınızı etkinleştirin" e-postası gönderilir (mevcut `/davet/:token` sayfası, **hiç değişmez**).
   - İlk dönem için `PlatformInvoice.issue(tenantId, planCode, plan.monthlyPrice, today, today.plusMonths(1), today)` kesilir, hemen ardından `RecordPlatformPaymentUseCase` ile (`method=CARD_ONLINE`, `recordedByAdminId=null`, `notes="iyzico odeme referansi: " + paymentId`) ödenmiş işaretlenir — tenant'ın "Faturalar" listesi ilk günden doğru görünür.
   - `signupRequest.complete()`.
   - `true` döner.
6. Ziyaretçi vetly.com'da (`?kayit=basarili` veya `?kayit=hata` query param'ına göre) bir sonuç bölümü görür: "Ödemeniz alındı! E-postanızı kontrol edin, hesabınızı etkinleştirme linki gönderdik."
7. Yönetici adayı e-postadaki linke tıklar → mevcut `/davet/:token` sayfası → şifresini belirler → `AcceptStaffInviteUseCase` çalışır (**hiç değişmez**) → giriş yapabilir.

## 5. `TenantAdminPort`'a Yeni Metod

```java
/**
 * Odeme sonrasi self-servis kayit icin: Tenant + Branch + Subscription
 * (secilen plan, ACTIVE durumda -- TRIAL DEGIL) olusturur ama StaffUser
 * OLUSTURMAZ -- ilk admin, ayri bir StaffInvite kabul ederek kendi
 * hesabini/sifresini olusturur (mevcut invite-accept akisiyla ayni).
 * createTenant(...)'tan farkli olarak burada admin sifresi CAGIRAN
 * TARAFTAN gelmiyor -- odeme yapan ziyaretci henuz hicbir sifre girmedi.
 */
TenantSignupResult createTenantForPaidSignup(
    String tenantName, String taxNumber, String branchName, String address, String city,
    String planCode, LocalDate renewsAt
);

/** Odeme oncesi e-posta benzersizligini kontrol etmek icin -- StaffUserRepository.existsByEmail'in ince bir sarmalayicisi. */
boolean isEmailRegistered(String email);
```
`TenantSignupResult(UUID tenantId, UUID branchId)` — yeni küçük bir record, `tenant.domain` paketinde. `isEmailRegistered`, `TenantAdminPortAdapter` içinde zaten var olan `staffUserJpaRepository.existsByEmail(...)` çağrısını (bkz. mevcut `createTenant`'ın ilk satırı) yeniden kullanır.

**Modül sınırı notu:** `com.vetos.modules.tenant.domain.exception` paketinin bir `@NamedInterface`'i yok (sadece `tenant.domain`'in kendisi `@NamedInterface("domain")` ile açık) — yani `platformadmin`, `EmailAlreadyRegisteredConflictException`'ı doğrudan import *edemez*. Bu yüzden §7'de e-posta çakışması için platformadmin'in kendi `SignupEmailAlreadyRegisteredConflictException`'ı tanımlanır (`#3`'teki `PaymentGatewayException`'ın platformadmin'e özel tanımlanmasıyla aynı desen) — `isEmailRegistered(...)` `true` dönerse bu fırlatılır.

Implementasyon (`TenantAdminPortAdapter`): `Tenant.register(...)` sonrası `tenant.activate()` çağrılır (TRIAL değil, gerçek ödeyen müşteri — `Tenant.status` alanı `TenantAdminPort.javadoc`'daki `createTenant`'tan farklı olarak burada TRIAL'da bırakılmaz). `Branch.create(...)` + `updateDetails(...)` — minimal formdan gelmeyen `taxNumber`/`address`/`city` için placeholder `"-"` değerleri `InitiateSignupCheckoutUseCase` tarafından geçirilir (bkz. §7). `Subscription`'a yeni bir factory: `Subscription.startPaid(tenantId, planCode, renewsAt)` (mevcut `startTrial(tenantId)`'nin yanına, `billingStatus=ACTIVE` ile). `StaffUser` **oluşturulmaz** — bu, mevcut `createTenant(...)`'tan tek temel fark.

`createTenant(...)` metodunun kendisi **hiç değişmez** — platform admin'in manuel akışı (admin şifreyi kendisi giriyor, davet beklemeden hemen giriş yapabiliyor) aynen kalır.

## 6. API

Yeni `PublicSignupController` (`/api/v1/public/signup`, kimlik doğrulama yok — mevcut `/api/v1/public/**` permitAll kuralına girer):

| Endpoint | Açıklama |
|---|---|
| `GET /plans` | Aktif planlar (mevcut `ListPlansUseCase`'in 3. reuse'u — `TenantBillingController.plans()` ve `PlanController.list()`'ten sonra) |
| `POST /checkout` | `{ clinicName, adminFullName, adminEmail, phone?, planCode }` → `{ checkoutFormUrl }`. E-posta zaten kayıtlıysa 409 (`SignupEmailAlreadyRegisteredConflictException`, yeni — bkz. §5 modül sınırı notu). |

`PublicPaymentCallbackController.handleAndRedirect` — §4 adım 4'teki try/catch fallback eklenir, **imza değişmez**, sadece iç mantık genişler.

## 7. Application Katmanı (Use Case'ler)

- `InitiateSignupCheckoutUseCase(clinicName, adminFullName, adminEmail, phone, planCode): CheckoutSession` — plan+email doğrulaması, `TenantSignupRequest` kaydı, `paymentGatewayPort.initializeCheckout(...)` çağrısı
- `HandleSignupPaymentCallbackUseCase(String token, LocalDate today): boolean` — `HandlePaymentCallbackUseCase` ile **birebir aynı imza şekli** (simetrik tasarım): kendi `paymentGatewayPort.retrieveCheckoutResult(token)` çağrısını kendisi yapar, `conversationId`'yi `TenantSignupRequestRepository.findById(...)` ile arar — bulamazsa yeni `TenantSignupRequestNotFoundException` fırlatır (controller'ın §4 adım 4c'deki catch-all'una düşer). Bulursa ve `success=false` ise `false` döner (tenant oluşturmadan). Bulursa ve `success=true` ise: zaten `COMPLETED` ise idempotent `true` (no-op); değilse §4 adım 5'teki tenant+branch+subscription+invite+fatura akışını çalıştırıp `signupRequest.complete()` çağırır, `true` döner.
- Placeholder değerler `HandleSignupPaymentCallbackUseCase`'in kendi içinde, `createTenantForPaidSignup(...)` çağrılırken sabit olarak kullanılır (iki ayrı HTTP isteği olduğu için `InitiateSignupCheckoutUseCase`'den taşınmaz — `TenantSignupRequest` zaten sadece §3.1'deki alanları saklıyor): `taxNumber="-"`, `address="-"`, `city="-"`, `branchName=signupRequest.clinicName()`. Klinik ilk giriş yaptığında panelden (Şubeler ayarı) gerçek bilgileri girer.

## 8. Frontend (vetly-site)

**`Pricing.tsx`:** Statik içerik yerine `GET /api/v1/public/signup/plans`'tan çekilen gerçek plan listesi render edilir (ad, aylık fiyat, açıklama, özellik listesi — `#1`'de zaten var olan alanlar). "Hemen Başla" butonu, tıklanan planın kodunu taşıyarak checkout formuna (`#iletisim`) scroll eder ve formda o plan önceden seçili gelir.

**`ContactCta.tsx` → checkout formu:** formsubmit.co'ya giden mevcut form kaldırılır. Yeni form alanları: klinik adı, yönetici adı-soyadı, e-posta, telefon (opsiyonel), plan seçici (Pricing'den gelen planla önceden dolu). Submit → `POST /api/v1/public/signup/checkout` → dönen `checkoutFormUrl`'e `window.location.href` ile tam sayfa yönlendirme.

**Yeni:** ödeme dönüşü sonrası `?kayit=basarili|hata` query param'ını okuyup ilgili banner'ı gösteren mantık (ana sayfada, `App.tsx` seviyesinde ya da `HomePage.tsx`'te — mevcut #3'teki `SubscriptionPanel.tsx` deseniyle aynı).

**`src/api/client.ts`** (yeni, vetly-site'a): ana projedeki `VITE_API_BASE_URL` deseninin birebir aynısı — `import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'`.

## 9. Config

`SecurityConfig.corsConfigurationSource()`'daki `allowedOriginPatterns`:
```java
configuration.setAllowedOriginPatterns(List.of("http://localhost:*", vetlySiteOrigin));
```
`vetlySiteOrigin` yeni bir `@Value("${app.vetly-site-origin:http://localhost:5175}")` — yerelde vetly-site'ın dev portu, prod'da gerçek `https://vetly.com` ile override edilir.

## 10. Hata Durumları

- E-posta zaten kayıtlı → checkout **başlamadan önce** `SignupEmailAlreadyRegisteredConflictException` ile 409, açık mesaj (ödeme yapıp sonra hata almak yerine).
- Plan bulunamadı/pasif → checkout başlamadan önce hata.
- Ödeme başarısız → `TenantSignupRequest` `PENDING` kalır, ziyaretçi tekrar dener (yeni bir istek yeni bir `TenantSignupRequest` oluşturur — eski `PENDING` kayıt sessizce terk edilir, temizlik gerekmez, veritabanında birkaç tamamlanmamış kayıt kalması zararsızdır).
- Callback'te `conversationId` ne fatura ne kayıt olarak bulunamazsa → `?odeme=hata` (mevcut callback'in "beklenmeyen hata → hata redirect" davranışıyla tutarlı).
- Callback tekrarlanırsa (idempotency) → `TenantSignupRequest.complete()` zaten `COMPLETED` ise no-op, `true` döner — ikinci bir tenant oluşturulmaz.

## 11. Test Stratejisi

- `TenantSignupRequest` domain testi (`create`, `complete` idempotency).
- `InitiateSignupCheckoutUseCase` testi: happy path, email-zaten-kayıtlı, plan-yok/pasif.
- `HandleSignupPaymentCallbackUseCase` testi: happy path (tenant+branch+subscription+invite+fatura oluşuyor mu, doğru alanlarla), idempotent tekrar çağrı, `success=false` sonucu tenant oluşturmuyor.
- `TenantAdminPortAdapter.createTenantForPaidSignup` için bir doğrulama (StaffUser oluşturulmadığının, Tenant.status'un ACTIVE olduğunun kontrolü).
- Yerel çalışan instance'a karşı curl ile uçtan uca (simüle mod): plan listele → checkout başlat → simüle callback → tenant oluştu mu, StaffInvite var mı, fatura PAID mi doğrula → `/davet/:token` ile şifre belirleyip giriş yap.

## 12. Açık Sorular

Yok — tasarım kullanıcı onayından geçti.
