# Self-Servis 7 Günlük Deneme — Uygulama Planı

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Veteriner vitrin sitesinden SMS doğrulamasıyla 7 günlük deneme hesabı açar; süre dolunca panel yalnızca ödeme sayfasını gösterir ve paket seçip ödeyerek üyeliğe geçilir; mevcut deneme hesapları süresiz test hesabına dönüşür.

**Architecture:** Deneme kaydı ve denemeden ücretliye geçiş, mevcut ödemeli kayıt akışının (`TenantSignupRequest` → iyzico → callback) yanına aynı desenle `modules/platformadmin` içine eklenir. Sürenin dolması durum olarak saklanmaz; `platform/subscription` altındaki yeni bir port + interceptor (FeatureAccessPort / FeatureGateInterceptor deseninin aynısı) her istekte tarih karşılaştırmasıyla karar verir. Fatura yalnızca ödeme başarılı olunca kesilir.

**Tech Stack:** Java 21, Spring Boot 3.5, Flyway, PostgreSQL, JUnit 5 + Mockito + AssertJ; React + TypeScript + Vite, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-11-13-self-servis-deneme-design.md`

## Global Constraints

- **Ön koşul:** Çalışma ağacındaki commit edilmemiş aylık/yıllık dönem çalışması (`BillingPeriod.java`, `Subscription.java`, `TenantAdminPort*.java`, `BillableSubscription.java`, `V101__subscription_billing_period.sql`) commit edilmeden Task 1'e başlanmaz. `vetly-site`'taki commit edilmemiş `index.html`, `Faq.tsx`, `Pricing.tsx` değişikliklerinin üzerine yazılmaz.
- Modül sınırı: bir modül başka modülün `domain` sınıfına doğrudan erişmez; `platform/` hiçbir modüle bağımlı olamaz. `ApplicationModulesTest` her task sonunda yeşil kalır.
- Yeni kod `docs/reference-module.md` katman sırasını ve `docs/coding-conventions.md` isimlendirmesini izler; test adları `should_<sonuç>_when_<koşul>`.
- HTTP durum kodu, `GlobalExceptionHandler`'daki istisna-adı son eki kuralıyla belirlenir (`...ConflictException` → 409 vb.). 402 ve 429 için son ek eşlemesi yoksa aynı desenle eklenir.
- Ödemeli kayıt akışının (`POST /api/v1/public/signup/checkout` ve callback'i) davranışı değişmez.
- Sağlayıcı kuralı: İleti Merkezi / SMTP / iyzico kimlik bilgisi yoksa adaptör simüle eder, hata fırlatmaz.
- Deneme süresi 7 gün. Dolmuşluk: `planCode == "TRIAL"` ve `renewsAt < bugün` (Europe/Istanbul).
- Ayrılmış plan kodları: `TRIAL`, `INTERNAL` (ikisinin de `plans` tablosunda satırı yoktur).
- SMS kodu: 6 hane, 300 sn geçerli, en fazla 5 yanlış deneme, tekrar gönderme en erken 60 sn sonra, istek başına en fazla 3 SMS, aynı telefona 24 saatte en fazla 3 kayıt isteği.
- Kilit ekranı metni birebir: `Deneme kullanım süreniz dolmuştur, lütfen üyelik başlatınız.`
- Frontend: ham hex yazılmaz (`var(--color-*)`); sayfada en fazla bir `primary` buton; deneme bandı AI mavisini kullanmaz, son 2 gün `warning` tonu.
- Commit mesajları projenin mevcut biçimini izler ve `Co-Authored-By: Claude Fable 5.2 <noreply@anthropic.com>` satırıyla biter. Commit'ler `main` yerine ayrı bir dalda atılır.

## Review Focus

1. **Aynı doğrulama isteğinin iki kez / eşzamanlı gönderilmesi** → tek klinik oluşur, ikinci çağrı aynı `loginUrl`'i döner (Task 5).
2. **Ödeme callback'inin tekrar gelmesi ya da kullanıcının ödemeyi yarıda bırakması** → ikinci fatura kesilmez; yarım kalan deneme hiçbir zaman `PAST_DUE`/`SUSPENDED` olmaz (Task 7).
3. **Deneme bitiş gününün sınırı** → `renewsAt` günü boyunca erişim açık, ertesi gün 00:00 (Istanbul) itibarıyla kilitli; sunucu saat dilimi UTC olsa bile (Task 1, Task 3).
4. **Telefonun farklı yazımlarla girilmesi** (`0532 111 22 33`, `+90532...`, `532...`) → hepsi aynı numara sayılır; tekrar deneme engeli atlatılamaz (Task 4).
5. **Kilitliyken panel dışı kanallar** → kliniğin public randevu formu, hatırlatma zamanlayıcıları ve TARBİL eklenti anahtarı da çalışmaz; `INTERNAL` hesap hiçbirinden etkilenmez (Task 3).

---

### Task 1: Ayrılmış plan kodları, deneme süresi ayarı, `Subscription` davranışı

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/subscription/ReservedPlanCodes.java`
- Create: `backend/src/main/java/com/vetos/platform/subscription/TrialProperties.java`
- Create: `backend/src/main/java/com/vetos/platform/subscription/package-info.java` (diğer `platform` alt paketleri gibi `@NamedInterface`)
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/Subscription.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java` (`createTenant` içindeki `startTrial` çağrısı)
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanFeatureAccessAdapter.java` (`TRIAL_PLAN_CODE` sabiti `ReservedPlanCodes.TRIAL`'a yönlenir)
- Modify: `backend/src/main/resources/application.yml` (`vetly.trial.*`)
- Test: `backend/src/test/java/com/vetos/modules/tenant/domain/SubscriptionTest.java`

**Interfaces:**
- Produces:
  - `ReservedPlanCodes.TRIAL = "TRIAL"`, `ReservedPlanCodes.INTERNAL = "INTERNAL"`, `static boolean isReserved(String planCode)`
  - `@ConfigurationProperties("vetly.trial") record TrialProperties(int days /*7*/, int otpTtlSeconds /*300*/, int otpMaxAttempts /*5*/, int otpResendCooldownSeconds /*60*/, int otpMaxSends /*3*/, int maxRequestsPerPhonePerDay /*3*/)` — `QuotaProperties` ile aynı `@DefaultValue` deseni
  - `Subscription.startTrial(UUID tenantId, int trialDays)` (tek parametreli sürüm kaldırılır)
  - `boolean Subscription.isTrialExpired(LocalDate today)`
  - `void Subscription.activatePaid(String planCode, LocalDate renewsAt, BillingPeriod billingPeriod)` → `billingStatus = ACTIVE`

- [ ] **Step 1: Başarısız testleri yaz** (`SubscriptionTest`)

```java
@Test void should_endAfterSevenDays_when_trialStartedWithSevenDays() {
    Subscription s = Subscription.startTrial(UUID.randomUUID(), 7);
    assertThat(s.getRenewsAt()).isEqualTo(LocalDate.now().plusDays(7));
    assertThat(s.getPlanCode()).isEqualTo("TRIAL");
}
@Test void should_notBeExpired_when_todayIsRenewalDay() {
    Subscription s = Subscription.startTrial(UUID.randomUUID(), 7);
    assertThat(s.isTrialExpired(s.getRenewsAt())).isFalse();
}
@Test void should_beExpired_when_todayIsAfterRenewalDay() {
    Subscription s = Subscription.startTrial(UUID.randomUUID(), 7);
    assertThat(s.isTrialExpired(s.getRenewsAt().plusDays(1))).isTrue();
}
@Test void should_neverBeExpired_when_planIsNotTrial() {
    Subscription s = Subscription.startPaid(UUID.randomUUID(), "PRO", LocalDate.now().minusDays(30));
    assertThat(s.isTrialExpired(LocalDate.now())).isFalse();
}
@Test void should_becomeActivePaid_when_activatePaidCalled() {
    Subscription s = Subscription.startTrial(UUID.randomUUID(), 7);
    s.activatePaid("PRO", LocalDate.now().plusMonths(12), BillingPeriod.ANNUAL);
    assertThat(s.getPlanCode()).isEqualTo("PRO");
    assertThat(s.getBillingStatus()).isEqualTo(BillingStatus.ACTIVE);
    assertThat(s.getBillingPeriod()).isEqualTo(BillingPeriod.ANNUAL);
    assertThat(s.isTrialExpired(LocalDate.now().plusYears(5))).isFalse();
}
```

- [ ] **Step 2:** `cd backend && ./mvnw test -Dtest=SubscriptionTest` → yeni testler derleme hatasıyla FAIL.
- [ ] **Step 3:** Yukarıdaki arayüzleri uygula. `TenantAdminPortAdapter`, `TrialProperties`'i enjekte edip `startTrial(tenantId, trialProperties.days())` çağırır. `SubscriptionTest`'teki mevcut 14 gün varsayan testler 7 güne/parametreye güncellenir.
- [ ] **Step 4:** `./mvnw test -Dtest='SubscriptionTest,ApplicationModulesTest,CreatePlatformTenantUseCaseTest'` → PASS.
- [ ] **Step 5:** Commit — `feat: deneme süresi yapılandırılabilir, 7 gün varsayılan`

---

### Task 2: `INTERNAL` test hesabı planı + mevcut denemelerin dönüştürülmesi

**Files:**
- Create: `backend/src/main/resources/db/migration/V102__existing_trials_to_internal.sql`
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanFeatureAccessAdapter.java`, `PlanUsageQuotaAdapter.java`, `PlanStaffUserLimitAdapter.java`, `PlanBranchLimitAdapter.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantAdminPortAdapter.java` (`listSubscriptionsDueOnOrBefore`: `TRIAL` gibi `INTERNAL` de hariç)
- Modify: `backend/src/main/java/com/vetos/modules/platformadmin/application/GetPlatformOverviewUseCase.java` + `application/dto/PlatformOverviewSummary.java` + `api/dto/PlatformOverviewResponse.java` (`internalTenants` sayacı; `INTERNAL` hesaplar `activeTenants` ve `trialBillingTenants`'a sayılmaz)
- Modify: `frontend/src/pages/platform-admin/TenantDetailPage.tsx`, `tenantBadges.ts`, `OverviewPage.tsx`, `frontend/src/api/platformAdminApi.ts`
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/infrastructure/adapter/PlanFeatureAccessAdapterTest.java`, `PlanUsageQuotaAdapterTest.java`, `backend/src/test/java/com/vetos/modules/platformadmin/application/GetPlatformOverviewUseCaseTest.java`

**Interfaces:**
- Consumes: `ReservedPlanCodes` (Task 1)
- Produces: `INTERNAL` plan kodlu kiracı için — tüm `Feature`'lar açık, AI/mesaj/şube/personel kotası sınırsız (`null`), faturalama listelerinde yok.

- [ ] **Step 1: Başarısız testleri yaz**

```java
// PlanFeatureAccessAdapterTest
@Test void should_enableAllFeatures_when_planIsInternalAndEnforcementOn() {
    when(planLookup.planCodeOf(tenantId)).thenReturn(Optional.of("INTERNAL"));
    assertThat(adapter(true).enabledFeatures(tenantId)).containsExactlyInAnyOrder(Feature.values());
}
// PlanUsageQuotaAdapterTest
@Test void should_reportUnlimitedQuota_when_planIsInternal() {
    when(planLookup.planCodeOf(tenantId)).thenReturn(Optional.of("INTERNAL"));
    UsageSnapshot usage = adapter(true).currentUsage(tenantId);
    assertThat(usage.ai().quota()).isNull();
    assertThat(usage.message().quota()).isNull();
}
// GetPlatformOverviewUseCaseTest
@Test void should_countInternalSeparately_when_tenantHasInternalPlan() { /* 1 INTERNAL + 1 TRIAL + 1 ücretli → internalTenants=1, trialBillingTenants=1 */ }
```

- [ ] **Step 2:** `./mvnw test -Dtest='PlanFeatureAccessAdapterTest,PlanUsageQuotaAdapterTest,GetPlatformOverviewUseCaseTest'` → FAIL.
- [ ] **Step 3:** Dört adaptörde `TRIAL` dalından önce `INTERNAL` dalı; faturalama filtresi; genel bakış sayacı.
- [ ] **Step 4:** Migration:

```sql
-- Yayın anındaki tüm deneme hesapları test ekibine aittir (spec §7): süresiz test hesabı olurlar.
UPDATE tenants SET status = 'ACTIVE'
 WHERE status = 'TRIAL' AND id IN (SELECT tenant_id FROM subscriptions WHERE plan_code = 'TRIAL');
UPDATE subscriptions SET plan_code = 'INTERNAL', billing_status = 'ACTIVE', renews_at = NULL
 WHERE plan_code = 'TRIAL';
```

- [ ] **Step 5:** Platform admin arayüzü: klinik detayındaki plan `datalist`'ine `INTERNAL` ("Test hesabı (süresiz)"); `tenantBadges.ts`'e `INTERNAL` için rozet (nötr ton, "Test Hesabı"); genel bakışta ayrı sayaç. `INTERNAL` seçilince yenileme tarihi alanı boş gönderilebilir.
- [ ] **Step 6:** `./mvnw test` (tümü) ve `cd frontend && npm run build && npm test` → PASS.
- [ ] **Step 7:** Commit — `feat: süresiz test hesabı planı (INTERNAL) ve mevcut denemelerin dönüştürülmesi`

---

### Task 3: Erişim kapısı (402 `TRIAL_EXPIRED`)

**Files:**
- Create: `backend/src/main/java/com/vetos/platform/subscription/SubscriptionAccessPort.java`, `SubscriptionAccess.java`, `SubscriptionAccessState.java`, `SubscriptionGateInterceptor.java`, `SubscriptionWebConfig.java`, `TrialExpiredPaymentRequiredException.java`
- Create: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/SubscriptionAccessAdapter.java`
- Create: `backend/src/main/java/com/vetos/modules/tenant/api/dto/SubscriptionAccessResponse.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/api/SubscriptionsController.java` (`GET /access`, tüm roller)
- Modify: `backend/src/main/java/com/vetos/modules/tenant/infrastructure/persistence/TenantLookupAdapter.java` (`findActiveTenantIds`, `isOperational` dolmuş denemeyi dışlar)
- Modify: kliniğe ait public uçların use-case'leri (`PublicClinicController`, `PublicAppointmentController`, `PublicPatientIntakeController` arkasındakiler) — `TenantLookupPort.isOperational(tenantId)` false ise mevcut "klinik bulunamadı" yanıtı
- Modify: `GlobalExceptionHandler` (402 eşlemesi yoksa)
- Test: `backend/src/test/java/com/vetos/platform/subscription/SubscriptionGateInterceptorTest.java`, `backend/src/test/java/com/vetos/modules/tenant/infrastructure/persistence/SubscriptionAccessAdapterTest.java`, `backend/src/test/java/com/vetos/TrialExpiryGateIntegrationTest.java`

**Interfaces:**
- Consumes: `Subscription.isTrialExpired`, `ReservedPlanCodes` (Task 1)
- Produces:
  - `enum SubscriptionAccessState { TRIAL, TRIAL_EXPIRED, INTERNAL, ACTIVE }` (`ACTIVE` = deneme/test dışındaki her şey)
  - `record SubscriptionAccess(SubscriptionAccessState state, LocalDate trialEndsAt, Integer daysLeft)` — `trialEndsAt`/`daysLeft` yalnız `TRIAL`/`TRIAL_EXPIRED` için dolu; `daysLeft = max(0, günFarkı(bugün, trialEndsAt))`
  - `interface SubscriptionAccessPort { SubscriptionAccess accessOf(UUID tenantId); void evict(UUID tenantId); }`
  - Hata: HTTP 402, `errorCode = "TRIAL_EXPIRED"`, mesaj Global Constraints'teki metin
  - `GET /api/v1/subscriptions/access` → `{ state, trialEndsAt, daysLeft }`
- Kapının izin verdiği yollar: `/api/v1/auth/**`, `/api/v1/subscriptions/**`. `TenantContext.currentOrNull() == null` ise (public, platform admin) kapı devreye girmez.
- Adaptör "bugün"ü `Clock` + `ZoneId.of("Europe/Istanbul")` ile hesaplar; sonucu kiracı başına 60 sn önbellekler (Caffeine, `PlanLookup` deseni), `evict` temizler.

- [ ] **Step 1: Başarısız testleri yaz**

```java
// SubscriptionAccessAdapterTest (Clock sabitlenir)
@Test void should_reportTrialWithDaysLeft_when_trialNotEnded()         // renewsAt = bugün+3 → TRIAL, daysLeft=3
@Test void should_reportTrial_when_todayIsLastDay()                    // renewsAt = bugün → TRIAL, daysLeft=0
@Test void should_reportExpired_when_istanbulDateIsPastRenewal()       // Clock = renewsAt+1 gün 21:30 UTC-önceki gün → Istanbul'da ertesi gün → TRIAL_EXPIRED
@Test void should_reportInternal_when_planIsInternal()
@Test void should_reportActive_when_planIsPaid()
// SubscriptionGateInterceptorTest
@Test void should_throwPaymentRequired_when_trialExpiredAndPathIsProtected()   // /api/v1/patients
@Test void should_pass_when_trialExpiredAndPathIsSubscriptions()               // /api/v1/subscriptions/access
@Test void should_pass_when_trialExpiredAndPathIsAuth()                        // /api/v1/auth/logout
@Test void should_pass_when_noTenantContext()
// TrialExpiryGateIntegrationTest (gerçek Postgres, mevcut entegrasyon testlerinin kurulumu)
@Test void should_return402WithTrialExpiredCode_when_expiredTrialCallsPatients()
@Test void should_return200_when_expiredTrialCallsSubscriptionAccess()
@Test void should_return200_when_internalTenantWithPastDatesCallsPatients()
@Test void should_excludeExpiredTrial_from_findActiveTenantIds()
@Test void should_rejectExtensionToken_when_trialExpired()
@Test void should_hidePublicClinic_when_trialExpired()
```

- [ ] **Step 2:** `./mvnw test -Dtest='SubscriptionAccessAdapterTest,SubscriptionGateInterceptorTest,TrialExpiryGateIntegrationTest'` → FAIL.
- [ ] **Step 3:** Uygula. `SubscriptionWebConfig`, `FeatureWebConfig` ile aynı biçimde interceptor'ı kaydeder.
- [ ] **Step 4:** Aynı komut → PASS; ardından `./mvnw test` → PASS.
- [ ] **Step 5:** `docs/api-conventions.md`'ye 402/`TRIAL_EXPIRED` maddesi, `docs/architecture.md`'ye `SubscriptionAccessPort` maddesi (kısa, mevcut biçimde).
- [ ] **Step 6:** Commit — `feat: süresi dolan denemede erişim kapısı (402 TRIAL_EXPIRED)`

---

### Task 4: `TrialSignupRequest` + kaydı başlatma ve kodu yeniden gönderme

**Files:**
- Create: `backend/src/main/resources/db/migration/V103__trial_signup_requests.sql`
- Create (`backend/src/main/java/com/vetos/modules/platformadmin/`):
  - `domain/TrialSignupRequest.java`, `TrialSignupRequestStatus.java`, `TrialSignupRequestRepository.java`, `TrialSmsPort.java`, `TrialOtpGenerator.java` (arayüz), `PhoneNumbers.java`
  - `domain/exception/TrialPhoneAlreadyUsedConflictException.java`, `TrialSignupTooManyRequestsException.java`, `TrialSignupRequestNotFoundException.java`
  - `application/StartTrialSignupUseCase.java`, `StartTrialSignupCommand.java`, `TrialSignupStarted.java`, `ResendTrialCodeUseCase.java`
  - `infrastructure/persistence/TrialSignupRequestJpaRepository.java`, `TrialSignupRequestRepositoryAdapter.java`
  - `infrastructure/adapter/IletiMerkeziTrialSmsAdapter.java`, `SecureRandomTrialOtpGenerator.java`
  - `api/dto/StartTrialSignupRequest.java`, `TrialSignupStartedResponse.java`, `ResendTrialCodeRequest.java`
- Modify: `infrastructure/adapter/IletiMerkeziPlatformBillingSmsAdapter.java` — HTTP gönderim gövdesi paket-içi `IletiMerkeziSmsClient`'a çıkarılır, iki adaptör onu paylaşır (davranış değişmez)
- Modify: `api/PublicSignupController.java` (`POST /trial/start`, `POST /trial/resend`)
- Modify: `backend/src/main/java/com/vetos/platform/web/RateLimitFilter.java` (`/api/v1/public/signup/trial/` için ayrı havuz: IP başına 5 dk'da 10)
- Test: `backend/src/test/java/com/vetos/modules/platformadmin/domain/TrialSignupRequestTest.java`, `PhoneNumbersTest.java`, `application/StartTrialSignupUseCaseTest.java`, `ResendTrialCodeUseCaseTest.java`, `backend/src/test/java/com/vetos/platform/web/RateLimitFilterTest.java`

**Interfaces:**
- Consumes: `TrialProperties` (Task 1), `TenantAdminPort.isEmailRegistered`, `PasswordEncoder`
- Produces:
  - `PhoneNumbers.toE164Tr(String raw) : String` — boşluk/tire/parantez atılır; `0532…`, `532…`, `90532…`, `+90532…` → `+90532…`; 10 haneli mobil (5 ile başlayan) değilse `IllegalArgumentException`
  - `record StartTrialSignupCommand(String fullName, String phone, String email, String clinicName, String city, String chamberRegistrationNo, String password, boolean consentAccepted, String clientIp)`
  - `record TrialSignupStarted(UUID requestId, int resendAvailableInSeconds)`
  - `StartTrialSignupUseCase.execute(StartTrialSignupCommand) : TrialSignupStarted`
  - `ResendTrialCodeUseCase.execute(UUID requestId) : int` (yeni bekleme süresi, sn)
  - `TrialSmsPort { void sendVerificationCode(String phoneE164, String code); void sendWelcome(String phoneE164, String clinicName, LocalDate trialEndsAt); void sendTrialEndingSoon(String phoneE164, String clinicName, LocalDate trialEndsAt); void sendTrialLastDay(String phoneE164, String clinicName); }`
  - `TrialOtpGenerator { String next(); }` — 6 haneli, baştaki sıfırlar korunur
  - `TrialSignupRequest` davranışı: `create(...)`, `issueCode(String otpHash, Instant now, int ttlSeconds)` (`otpSendCount++`), `boolean canResend(Instant now, int cooldownSeconds, int maxSends)`, `registerFailedAttempt()`, `boolean isCodeExpired(Instant now)`, `boolean hasAttemptsLeft(int maxAttempts)`, `complete(UUID tenantId, Instant now)`, `isCompleted()`
  - `TrialSignupRequestRepository`: `save`, `findById`, `findByIdForUpdate`, `existsByPhoneAndStatus(String phoneE164, TrialSignupRequestStatus)`, `countByPhoneAndCreatedAtAfter(String phoneE164, Instant since)`, `findAllNewestFirst()`
  - Tablo `trial_signup_requests`: `id uuid pk, full_name, email, phone, clinic_name, city, chamber_registration_no, password_hash, otp_hash, otp_expires_at timestamptz, otp_attempts int, otp_send_count int, last_otp_sent_at timestamptz, status text, tenant_id uuid null, consent_accepted_at timestamptz, consent_ip text, created_at timestamptz, completed_at timestamptz null`; indeksler `(phone, status)`, `(email)`
  - HTTP: `POST /api/v1/public/signup/trial/start` → 200 `{ requestId, resendAvailableInSeconds }`; `POST /trial/resend` `{ requestId }` → 200 `{ resendAvailableInSeconds }`
- İstek doğrulaması (`StartTrialSignupRequest`): tüm metin alanları `@NotBlank`; `email` `@Email`; `password` en az 8 karakter; `consentAccepted` `@AssertTrue`; `chamberRegistrationNo` en fazla 30 karakter.
- Hata kodları: `SIGNUP_EMAIL_ALREADY_REGISTERED` (mevcut, 409), `TRIAL_PHONE_ALREADY_USED` (409), `TRIAL_RATE_LIMITED` (429), `TRIAL_SIGNUP_REQUEST_NOT_FOUND` (404).
- İleti Merkezi ayar anahtarları `platform-billing.ileti-merkezi.*` yeniden kullanılır. Simülasyonda doğrulama kodu log'a **maskelenmeden** yazılır (yalnız simülasyon dalında).
- SMS metni (doğrulama): `Vetly dogrulama kodunuz: %s. Kod 5 dakika gecerlidir.`

- [ ] **Step 1: Başarısız testleri yaz**

```java
// PhoneNumbersTest
@ParameterizedTest @ValueSource(strings = {"0532 111 22 33", "+90 532 111 22 33", "5321112233", "90(532)111-22-33"})
void should_normalizeToSameE164_when_phoneWrittenDifferently(String raw) {
    assertThat(PhoneNumbers.toE164Tr(raw)).isEqualTo("+905321112233");
}
@Test void should_reject_when_phoneIsNotTurkishMobile() // "0212 111 22 33", "123"
// StartTrialSignupUseCaseTest
@Test void should_saveRequestAndSendCode_when_validCommand()            // smsPort.sendVerificationCode("+905321112233", "123456"); kayıtta otpHash != "123456", passwordHash != düz şifre
@Test void should_throwConflict_when_emailAlreadyRegistered()
@Test void should_throwConflict_when_phoneHasCompletedTrial()           // "0532 111 22 33" girilir, "+905321112233" ile COMPLETED kayıt var
@Test void should_throwTooManyRequests_when_phoneHasThreeRequestsInLast24h()
@Test void should_notSendSms_when_validationFails()
// ResendTrialCodeUseCaseTest
@Test void should_throwTooManyRequests_when_resendBeforeSixtySeconds()
@Test void should_throwTooManyRequests_when_threeCodesAlreadySent()
@Test void should_issueNewCodeAndResetAttempts_when_resendAllowed()
@Test void should_throwNotFound_when_requestAlreadyCompleted()
// RateLimitFilterTest
@Test void should_return429_when_eleventhTrialStartFromSameIpWithinFiveMinutes()
```

- [ ] **Step 2:** `./mvnw test -Dtest='PhoneNumbersTest,TrialSignupRequestTest,StartTrialSignupUseCaseTest,ResendTrialCodeUseCaseTest,RateLimitFilterTest'` → FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** Aynı komut + `IletiMerkeziPlatformBillingSmsAdapter` mevcut testleri → PASS.
- [ ] **Step 5:** Commit — `feat: deneme kaydı başlatma ve SMS doğrulama kodu`

---

### Task 5: Kodu doğrulama ve kliniğin oluşturulması

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/VerifyTrialSignupUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/domain/TrialEmailPort.java`, `infrastructure/adapter/SmtpTrialEmailAdapter.java` (+ `resources` altında mevcut e-posta şablonlarının yanına `trial-welcome` şablonu)
- Create: `domain/exception/TrialCodeInvalidException.java`, `TrialCodeExpiredException.java`
- Create: `api/dto/VerifyTrialSignupRequest.java`, `TrialSignupVerifiedResponse.java`
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java`, `infrastructure/persistence/TenantAdminPortAdapter.java`
- Modify: `api/PublicSignupController.java` (`POST /trial/verify`)
- Test: `application/VerifyTrialSignupUseCaseTest.java`, `backend/src/test/java/com/vetos/TrialSignupIntegrationTest.java`

**Interfaces:**
- Consumes: Task 4'ün tümü; `TrialProperties`; `@Value("${app.frontend-base-url}")`
- Produces:
  - `TenantAdminPort.createTrialTenant(String tenantName, String city, String adminFullName, String adminEmail, String adminPasswordHash, String adminPhoneE164, String licenseNumber) : UUID` — `createTenant` ile aynı adımlar; farkları: şifre hash'i yeniden kodlanmaz, `taxNumber = "-"`, `branchName = tenantName`, `address = "-"`, yöneticinin `phone` ve `licenseNumber` alanları doldurulur, `ClinicRegisteredEvent` yayınlanır
  - `VerifyTrialSignupUseCase.execute(UUID requestId, String code) : String loginUrl`
  - `loginUrl = {app.frontend-base-url}/login?email={urlEncoded}&deneme=1`
  - `TrialEmailPort { void sendWelcome(String email, String fullName, String clinicName, LocalDate trialEndsAt, String loginUrl); void sendTrialEndingSoon(String email, String clinicName, LocalDate trialEndsAt, String upgradeUrl); void sendTrialLastDay(String email, String clinicName, String upgradeUrl); }`
  - HTTP: `POST /api/v1/public/signup/trial/verify` `{ requestId, code }` → 200 `{ loginUrl }`
  - Hata kodları: `TRIAL_CODE_INVALID` (422, yanıt mesajında kalan deneme sayısı), `TRIAL_CODE_EXPIRED` (422)
- Sıra: `findByIdForUpdate` → `COMPLETED` ise doğrudan `loginUrl` döner (kod kontrol edilmez) → süre/deneme hakkı kontrolü → kod eşleşmezse `registerFailedAttempt` **kaydedilip** istisna fırlatılır (istisna transaction'ı geri almamalı: sayaç artışı ayrı transaction'da ya da `noRollbackFor` ile) → e-posta ve telefon çakışması **yeniden** kontrol edilir → `createTrialTenant` → `complete` → hoş geldiniz SMS + e-posta (gönderim hatası kaydı geri almaz, yalnız log'lanır).
- Hoş geldiniz SMS metni: `Vetly'ye hos geldiniz! %s icin 7 gunluk denemeniz basladi, bitis: %s. Giris: %s`

- [ ] **Step 1: Başarısız testleri yaz**

```java
// VerifyTrialSignupUseCaseTest
@Test void should_createTenantAndReturnLoginUrl_when_codeMatches()
      // createTrialTenant("Pati Klinik","İzmir","Ayşe Yılmaz","ayse@ornek.com", <kayıttaki hash>, "+905321112233", "35-1234")
      // loginUrl == "http://localhost:5173/login?email=ayse%40ornek.com&deneme=1"
@Test void should_sendWelcomeSmsAndEmail_when_tenantCreated()
@Test void should_throwInvalidAndCountAttempt_when_codeWrong()
@Test void should_throwInvalid_when_fiveAttemptsUsedEvenIfCodeCorrect()
@Test void should_throwExpired_when_codeOlderThanFiveMinutes()
@Test void should_returnLoginUrlWithoutCreatingAgain_when_requestAlreadyCompleted()
@Test void should_throwConflict_when_emailRegisteredBetweenStartAndVerify()
@Test void should_stillComplete_when_welcomeSmsFails()
// TrialSignupIntegrationTest (gerçek Postgres; TrialOtpGenerator test çiftiyle sabit "123456")
@Test void should_allowLoginWithChosenPassword_when_signupVerified()     // start → verify → POST /api/v1/auth/login 200
@Test void should_startSevenDayTrial_when_signupVerified()               // subscription: TRIAL, renewsAt = bugün+7
@Test void should_createSingleTenant_when_verifyCalledTwiceConcurrently()
@Test void should_persistFailedAttempt_when_wrongCodeSubmitted()         // yanlış kod sonrası otp_attempts = 1
```

- [ ] **Step 2:** `./mvnw test -Dtest='VerifyTrialSignupUseCaseTest,TrialSignupIntegrationTest'` → FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** Aynı komut + `ApplicationModulesTest` → PASS.
- [ ] **Step 5:** Commit — `feat: SMS kodu doğrulanınca deneme kliniği oluşturulur`

---

### Task 6: Denemeden ücretliye geçiş — ödeme başlatma

**Files:**
- Create: `backend/src/main/resources/db/migration/V104__trial_upgrade_requests.sql`
- Create (`modules/platformadmin`): `domain/TrialUpgradeRequest.java`, `TrialUpgradeRequestStatus.java`, `TrialUpgradeRequestRepository.java`, `domain/exception/SubscriptionNotOnTrialException.java`, `application/InitiateTrialUpgradeCheckoutUseCase.java`, `application/CheckoutPricing.java`, `infrastructure/persistence/TrialUpgradeRequestJpaRepository.java`, `TrialUpgradeRequestRepositoryAdapter.java`, `api/dto/InitiateTrialUpgradeCheckoutRequest.java`
- Modify: `application/InitiateSignupCheckoutUseCase.java` (fiyat + kupon hesabı `CheckoutPricing`'e taşınır; davranış aynı)
- Modify: `api/TenantBillingController.java` (`POST /api/v1/subscriptions/upgrade-checkout`, `@PreAuthorize("hasRole('ADMIN')")`)
- Test: `application/InitiateTrialUpgradeCheckoutUseCaseTest.java`, `application/CheckoutPricingTest.java`

**Interfaces:**
- Consumes: `PlanRepository`, `CouponRepository`, `PaymentGatewayPort.initializeCheckout`, `TenantAdminPort.findPlanCode/getOverview/findBillingContactEmail`, `ReservedPlanCodes`
- Produces:
  - `CheckoutPricing.price(String planCode, BillingPeriod period, String couponCode, LocalDate today) : PricedCheckout` — `record PricedCheckout(Plan plan, BigDecimal chargedAmount, String normalizedCouponCode)`; geçersiz paket → `PlanNotFoundException`, yıllık fiyatı olmayan paket → mevcut `AnnualPlanUnavailableException`, geçersiz kupon → `CouponNotRedeemableException`
  - `TrialUpgradeRequest.create(UUID tenantId, String planCode, BillingPeriod period, String couponCode, BigDecimal chargedAmount)`, `complete()`, `isCompleted()`
  - `TrialUpgradeRequestRepository`: `save`, `findById`, `findByIdForUpdate`
  - `InitiateTrialUpgradeCheckoutUseCase.execute(UUID tenantId, String planCode, BillingPeriod period, String couponCode) : CheckoutSession` — kiracının planı `TRIAL` değilse `SubscriptionNotOnTrialException` (422 `SUBSCRIPTION_NOT_ON_TRIAL`); `planCode` ayrılmış bir kodsa `PlanNotFoundException`
  - HTTP: `POST /api/v1/subscriptions/upgrade-checkout` `{ planCode, billingPeriod, couponCode? }` → 200 `{ checkoutFormUrl }`
  - Tablo `trial_upgrade_requests`: `id uuid pk, tenant_id uuid, plan_code text, billing_period text, coupon_code text null, charged_amount numeric, status text, created_at timestamptz`
- Bu adımda **fatura kesilmez**, abonelik değişmez.

- [ ] **Step 1: Başarısız testleri yaz**

```java
@Test void should_createRequestAndReturnCheckoutUrl_when_tenantOnTrial()
      // initializeCheckout(request.getId().toString(), new BigDecimal("1490.00"), "Pati Klinik", "ayse@ornek.com")
@Test void should_chargeAnnualPrice_when_periodIsAnnual()
@Test void should_applyCoupon_when_couponRedeemable()
@Test void should_throwNotOnTrial_when_tenantAlreadyPaid()
@Test void should_throwNotOnTrial_when_tenantIsInternal()
@Test void should_throwPlanNotFound_when_planCodeIsReserved()            // "TRIAL", "INTERNAL"
@Test void should_notTouchInvoicesOrSubscription_when_checkoutInitiated() // verifyNoInteractions(platformInvoiceRepository); never updateSubscription
```

- [ ] **Step 2:** `./mvnw test -Dtest='InitiateTrialUpgradeCheckoutUseCaseTest,CheckoutPricingTest,InitiateSignupCheckoutUseCaseTest'` → FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** Aynı komut → PASS.
- [ ] **Step 5:** Commit — `feat: denemeden ücretli pakete geçiş için ödeme başlatma`

---

### Task 7: Denemeden ücretliye geçiş — ödeme callback'i

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/HandleTrialUpgradePaymentCallbackUseCase.java`
- Modify: `api/PublicPaymentCallbackController.java` (zincire üçüncü halka)
- Modify: `backend/src/main/java/com/vetos/modules/tenant/domain/TenantAdminPort.java`, `infrastructure/persistence/TenantAdminPortAdapter.java`
- Test: `application/HandleTrialUpgradePaymentCallbackUseCaseTest.java`, `backend/src/test/java/com/vetos/TrialUpgradeIntegrationTest.java`

**Interfaces:**
- Consumes: Task 6; `Subscription.activatePaid` (Task 1); `SubscriptionAccessPort.evict` (Task 3); `RecordPlatformPaymentUseCase`; `PlatformInvoice.issue`
- Produces:
  - `TenantAdminPort.activatePaidPlan(UUID tenantId, String planCode, LocalDate renewsAt, BillingPeriod period)` — aboneliği `activatePaid` ile günceller, `Tenant.activate()` çağırır
  - `HandleTrialUpgradePaymentCallbackUseCase.execute(String token, LocalDate today) : boolean`
- Adımlar (başarılı ödemede, tek transaction): `findByIdForUpdate` → `COMPLETED` ise `true` → `renewsAt = period.endDate(today)` → `activatePaidPlan` → `PlatformInvoice.issue(tenantId, planCode, chargedAmount, today, renewsAt, today)` kaydedilir → `RecordPlatformPaymentUseCase` (`CARD_ONLINE`, not `"iyzico odeme referansi: " + paymentId`, `recordedByAdminId = null`) → kupon varsa `incrementRedemption` → `complete()` → `subscriptionAccessPort.evict(tenantId)`.
- Controller: `conversationId` önce `TenantSignupRequest`, sonra `TrialUpgradeRequest` olarak aranır; ikincisiyse bu use-case çağrılır ve mevcut `redirectToApp(success)` kullanılır (`/ayarlar/abonelik?odeme=basarili|hata`).
- Başarısız ödemede hiçbir kayıt değişmez; kiracı denemede (ya da kilitli) kalır.

- [ ] **Step 1: Başarısız testleri yaz**

```java
// HandleTrialUpgradePaymentCallbackUseCaseTest
@Test void should_activatePlanIssuePaidInvoiceAndEvictAccess_when_paymentSucceeds()
      // activatePaidPlan(tenantId, "PRO", today.plusMonths(1), MONTHLY); fatura tutarı == request.chargedAmount; evict(tenantId)
@Test void should_setAnnualRenewal_when_periodIsAnnual()                // renewsAt == today.plusMonths(12)
@Test void should_changeNothing_when_paymentFails()
@Test void should_doNothingAndReturnTrue_when_callbackRepeated()        // tek fatura, tek activatePaidPlan
@Test void should_incrementCouponRedemption_when_couponUsed()
// TrialUpgradeIntegrationTest (gerçek Postgres, simüle ödeme)
@Test void should_unlockPanel_when_expiredTrialPays()                   // 402 → ödeme → GET /api/v1/patients 200, /subscriptions/access state=ACTIVE
@Test void should_leaveNoOpenInvoice_when_checkoutAbandoned()           // upgrade-checkout çağrılır, callback gelmez → platform_invoices'ta kiracıya ait satır yok
@Test void should_notSuspendOrFlagTrial_when_dailyBillingRunsAfterAbandonedCheckout() // PlatformBillingScheduler adımları sonrası billingStatus=TRIAL, status!=SUSPENDED
@Test void should_keepPaidSignupFlowWorking_when_callbackIsForSignupRequest()
```

- [ ] **Step 2:** `./mvnw test -Dtest='HandleTrialUpgradePaymentCallbackUseCaseTest,TrialUpgradeIntegrationTest'` → FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** Aynı komut + `PaymentConsistencyTest` + `./mvnw test` → PASS.
- [ ] **Step 5:** Commit — `feat: ödeme onaylanınca deneme ücretli üyeliğe döner`

---

### Task 8: Deneme hatırlatmaları

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/RemindEndingTrialsUseCase.java`
- Create: `backend/src/main/java/com/vetos/modules/tenant/domain/EndingTrial.java`
- Modify: `TenantAdminPort.java`, `TenantAdminPortAdapter.java`
- Modify: `infrastructure/scheduling/PlatformBillingScheduler.java` (beşinci adım, kendi `try/catch`'inde)
- Modify: `SmtpTrialEmailAdapter` (iki hatırlatma şablonu), `IletiMerkeziTrialSmsAdapter` (iki metin)
- Test: `application/RemindEndingTrialsUseCaseTest.java`

**Interfaces:**
- Consumes: `TrialSmsPort`, `TrialEmailPort` (Task 4, 5); `TenantAdminPort.findBillingContactEmail/Phone`, `getOverview`
- Produces:
  - `record EndingTrial(UUID tenantId, LocalDate endsAt)`
  - `TenantAdminPort.listTrialsEndingOn(LocalDate date) : List<EndingTrial>` — `planCode == TRIAL` ve `renewsAt == date`
  - `RemindEndingTrialsUseCase.execute(LocalDate today) : int` — `today.plusDays(2)` bitenlere "2 gün kaldı", `today` bitenlere "bugün son gün"; `upgradeUrl = {app.frontend-base-url}/ayarlar/abonelik`
- SMS metinleri: `Vetly: %s icin denemenizin bitmesine 2 gun kaldi (%s). Uyelik baslatmak icin panelinize giris yapin.` / `Vetly: %s icin denemeniz bugun sona eriyor. Kesintisiz devam etmek icin uyeliginizi baslatin.`

- [ ] **Step 1: Başarısız testleri yaz**

```java
@Test void should_sendEndingSoon_when_trialEndsInTwoDays()
@Test void should_sendLastDay_when_trialEndsToday()
@Test void should_sendNothing_when_trialEndsInThreeDaysOrTomorrow()
@Test void should_continueWithNextTenant_when_oneNotificationFails()
@Test void should_skipSms_when_billingContactHasNoPhone()
```

- [ ] **Step 2:** `./mvnw test -Dtest=RemindEndingTrialsUseCaseTest` → FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** Aynı komut → PASS.
- [ ] **Step 5:** Commit — `feat: deneme bitişi için SMS ve e-posta hatırlatmaları`

---

### Task 9: Platform admin — deneme kayıtları ve oda sicil numarası

**Files:**
- Create: `backend/src/main/java/com/vetos/modules/platformadmin/application/ListTrialSignupRequestsUseCase.java`, `api/dto/TrialSignupRequestResponse.java`
- Modify: `api/TenantSignupRequestsController.java` (`GET /api/v1/platform-admin/signup-requests/trials`)
- Modify: `frontend/src/api/platformAdminApi.ts`, kayıt isteklerini listeleyen platform admin sayfası (`listSignupRequests` çağıran sayfa), `frontend/src/pages/platform-admin/TenantDetailPage.tsx`
- Test: `application/ListTrialSignupRequestsUseCaseTest.java`, ilgili sayfanın Vitest dosyası

**Interfaces:**
- Consumes: `TrialSignupRequestRepository.findAllNewestFirst()` (Task 4)
- Produces: `GET .../signup-requests/trials` → `[{ id, fullName, email, phone, clinicName, city, chamberRegistrationNo, status, tenantId, createdAt, completedAt }]` (`passwordHash`/`otpHash` asla dönmez). Klinik detayında, kliniğe ait tamamlanmış deneme kaydı varsa oda sicil numarası ve deneme bitiş tarihi gösterilir.

- [ ] **Step 1:** Başarısız testler: `should_listNewestFirst_when_trialRequestsExist`, `should_notExposeHashes_when_mappedToResponse` (yanıt JSON'unda `passwordHash`/`otpHash` anahtarı yok); frontend: tablo oda sicil no sütununu ve durum rozetini gösterir.
- [ ] **Step 2:** `./mvnw test -Dtest=ListTrialSignupRequestsUseCaseTest` ve `cd frontend && npm test` → FAIL.
- [ ] **Step 3:** Uygula. Mevcut ödemeli kayıt listesinin yanına "Deneme Kayıtları" bölümü; yeni üst menü öğesi eklenmez.
- [ ] **Step 4:** Aynı komutlar + `npm run build` → PASS.
- [ ] **Step 5:** Commit — `feat: platform admin'de deneme kayıtları ve oda sicil numarası`

---

### Task 10: Panel — erişim durumu, kilit yönlendirmesi, `/uyelik` sayfası, paket seçici

**Files:**
- Create: `frontend/src/auth/SubscriptionAccessContext.tsx`
- Create: `frontend/src/components/subscription/PlanPicker.tsx`, `PlanPicker.module.css`, `PlanPicker.test.tsx`
- Create: `frontend/src/pages/subscription/MembershipPage.tsx`, `MembershipPage.module.css`, `MembershipPage.test.tsx`
- Modify: `frontend/src/api/client.ts` (`setPaymentRequiredHandler`), `frontend/src/api/subscriptionApi.ts`, `frontend/src/auth/RequireAuth.tsx`, `frontend/src/App.tsx` (`/uyelik` rotası), sağlayıcıların sarıldığı kök dosya (`FeatureProvider`'ın yanına)
- Modify: `frontend/src/pages/settings/SubscriptionPanel.tsx` (deneme kullanıcısına `PlanPicker`)
- Test: `frontend/src/auth/RequireAuth.test.tsx`

**Interfaces:**
- Consumes: `GET /api/v1/subscriptions/access` (Task 3), `POST /api/v1/subscriptions/upgrade-checkout` (Task 6), mevcut `subscriptionApi.getPlans()`
- Produces:
  - `subscriptionApi.getAccess(): Promise<SubscriptionAccess>`; `type SubscriptionAccess = { state: 'TRIAL' | 'TRIAL_EXPIRED' | 'INTERNAL' | 'ACTIVE'; trialEndsAt: string | null; daysLeft: number | null }`
  - `subscriptionApi.initiateUpgradeCheckout(payload: { planCode: string; billingPeriod: 'MONTHLY' | 'ANNUAL'; couponCode?: string }): Promise<CheckoutSession>`
  - `SubscriptionAccessProvider`, `useSubscriptionAccess(): { access: SubscriptionAccess | null; markExpired: () => void; reload: () => void }` — yüklenirken/yüklenemezse `null` (kilit varsayılmaz; asıl koruma sunucuda)
  - `setPaymentRequiredHandler(handler)` — 402 + `errorCode === 'TRIAL_EXPIRED'` yanıtında çağrılır; sağlayıcı bunu `markExpired`'a bağlar
  - `<PlanPicker plans onSelect(planCode, billingPeriod, couponCode?) submittingPlanCode error />`
  - `RequireAuth`: `access?.state === 'TRIAL_EXPIRED'` ise `<Navigate to="/uyelik" replace />`
- `/uyelik`: oturum yoksa `/login`; durum `TRIAL_EXPIRED` değilse `/panel`. ADMIN: Global Constraints'teki metin + `PlanPicker`; paket seçilince `checkoutFormUrl`'e tam sayfa yönlenir. Diğer roller: `Deneme süresi doldu, klinik yöneticinizle iletişime geçin.` Her iki durumda çıkış bağlantısı. Yerleşim `AuthLayout` (kenar çubuğu yok).
- `PlanPicker`: aylık/yıllık geçişi (yıllık fiyatı `null` olan pakette yıllık seçilemez), "Kupon kodum var" alanı, rozetli paketin butonu `primary`, diğerleri `secondary`; rozetli paket yoksa ilk paket `primary`.
- `SubscriptionPanel`: `billingStatus === 'TRIAL'` iken "Bizimle İletişime Geçin" satırı yerine `PlanPicker`; diğer durumlar değişmez.

- [ ] **Step 1: Başarısız testleri yaz**

```tsx
// RequireAuth.test.tsx
it('süresi dolmuş denemede /uyelik sayfasına yönlendirir')
it('erişim durumu yüklenemediğinde sayfayı gösterir')
// MembershipPage.test.tsx
it('ADMIN için uyarı metnini ve paketleri gösterir')        // getByText('Deneme kullanım süreniz dolmuştur, lütfen üyelik başlatınız.')
it('ADMIN olmayan role yöneticiyle iletişime geçin mesajını gösterir ve paket göstermez')
it('paket seçilince ödeme adresine yönlenir')               // initiateUpgradeCheckout({ planCode: 'PRO', billingPeriod: 'MONTHLY' })
it('ödeme başlatılamazsa hata mesajını gösterir')
// PlanPicker.test.tsx
it('yalnızca bir primary buton içerir')
it('yıllık fiyatı olmayan pakette yıllık seçimi devre dışıdır')
it('yıllık seçilince yıllık fiyatı gösterir ve ANNUAL gönderir')
```

- [ ] **Step 2:** `cd frontend && npm test` → yeni testler FAIL.
- [ ] **Step 3:** Uygula.
- [ ] **Step 4:** `npm test && npm run build` → PASS.
- [ ] **Step 5:** Commit — `feat: süresi dolan denemede üyelik sayfası ve paket seçici`

---

### Task 11: Panel — deneme bandı ve giriş sayfası

**Files:**
- Create: `frontend/src/components/subscription/TrialBanner.tsx`, `TrialBanner.module.css`, `TrialBanner.test.tsx`
- Modify: `frontend/src/components/layout/AppShell.tsx`, `AppShell.test.tsx`
- Modify: `frontend/src/pages/LoginPage.tsx` (+ testi)

**Interfaces:**
- Consumes: `useSubscriptionAccess` (Task 10), `useAuth`
- Produces: `<TrialBanner />` — yalnız `state === 'TRIAL'` iken görünür. Metin: `daysLeft > 0` → `Denemenizin bitmesine {daysLeft} gün kaldı.`; `daysLeft === 0` → `Denemeniz bugün sona eriyor.` ADMIN için `/ayarlar/abonelik`'e giden `Üyelik Başlat` bağlantısı (`tertiary`). `daysLeft <= 2` iken `warning` tonu, aksi halde nötr.
- `LoginPage`: `?email=` varsa e-posta alanı dolu gelir; `?deneme=1` varsa formun üstünde `Hesabınız oluşturuldu. Şifrenizle giriş yapın.` notu.

- [ ] **Step 1:** Başarısız testler: `7 gün kaldığında nötr bant gösterir`, `2 gün kaldığında warning tonu kullanır`, `son gün "bugün sona eriyor" yazar`, `INTERNAL ve ACTIVE durumda bant göstermez`, `ADMIN olmayan role Üyelik Başlat bağlantısı göstermez`, `email parametresiyle gelindiğinde e-postayı doldurur`, `deneme=1 ile gelindiğinde bilgi notunu gösterir`.
- [ ] **Step 2:** `npm test` → FAIL.
- [ ] **Step 3:** Uygula. Bant `AppShell`'de `<main>` içinde, sayfa içeriğinin üstünde.
- [ ] **Step 4:** `npm test && npm run build` → PASS.
- [ ] **Step 5:** Commit — `feat: panelde deneme bandı ve kayıt sonrası giriş notu`

---

### Task 12: Vitrin sitesi — `/deneme` kayıt sayfası

**Çalışma dizini:** `C:\Users\Furkan\Desktop\vetly-site` (ayrı git deposu; kendi dalında commit edilir).

**Files:**
- Create: `src/pages/TrialSignupPage.tsx`, `src/lib/turkishCities.ts`
- Modify: `src/api/signupApi.ts`, `src/api/client.ts` (`ApiError`'a `errorCode`), `src/App.tsx` (`/deneme`), `src/components/Hero.tsx`, `src/components/Header.tsx`, `src/components/Pricing.tsx`, `src/components/Faq.tsx`, `src/index.css`
- Test: sitede test altyapısı yok; doğrulama `npm run build` + elle tarayıcı kontrolüyle yapılır (yeni bağımlılık eklenmez).

**Interfaces:**
- Consumes: `POST /api/v1/public/signup/trial/start|verify|resend` (Task 4, 5)
- Produces:
  - `signupApi.startTrial(payload: { fullName; phone; email; clinicName; city; chamberRegistrationNo; password; consentAccepted: true }): Promise<{ requestId: string; resendAvailableInSeconds: number }>`
  - `signupApi.verifyTrial(payload: { requestId; code }): Promise<{ loginUrl: string }>`
  - `signupApi.resendTrialCode(payload: { requestId }): Promise<{ resendAvailableInSeconds: number }>`
  - `TURKISH_CITIES: string[]` (81 il, alfabetik)
- Sayfa iki adım: (1) form — alanlar spec §3.1; il `<select>`; onay kutusu `/kullanim-sartlari` ve `/gizlilik-politikasi` bağlantılı ve zorunlu; (2) 6 haneli kod girişi + geri sayımlı "Kodu tekrar gönder". Doğrulama başarılıysa `window.location.href = loginUrl`.
- Hata metinleri `errorCode`'a göre: `SIGNUP_EMAIL_ALREADY_REGISTERED` → `Bu e-posta ile zaten bir hesap var. Giriş yapmayı deneyin.`; `TRIAL_PHONE_ALREADY_USED` → `Bu telefon numarasıyla daha önce deneme başlatılmış.`; `TRIAL_CODE_INVALID` → sunucu mesajı (kalan deneme sayısıyla); `TRIAL_CODE_EXPIRED` → `Kodun süresi doldu. Yeni kod isteyin.`; `TRIAL_RATE_LIMITED` ve HTTP 429 → `Çok fazla deneme yapıldı. Lütfen biraz sonra tekrar deneyin.`; diğer → sunucu mesajı.
- Hero ve Header'daki ana buton `7 Gün Ücretsiz Dene` → `/deneme`. Mevcut ödemeli form (`#iletisim`) ve Pricing'deki "Hemen Başla" yerinde kalır; Pricing'e "veya 7 gün ücretsiz deneyin" bağlantısı, SSS'ye deneme maddesi eklenir. **`Pricing.tsx` ve `Faq.tsx`'teki commit edilmemiş mevcut değişiklikler korunur.**

- [ ] **Step 1:** `git -C /c/Users/Furkan/Desktop/vetly-site status --short` çıktısını kaydet; dokunulacak dosyalardaki mevcut değişiklikleri oku.
- [ ] **Step 2:** `signupApi` + `client.ts` + `turkishCities.ts` + sayfa + rota.
- [ ] **Step 3:** Buton/metin güncellemeleri.
- [ ] **Step 4:** `npm run build` → hatasız. Backend lokalde açıkken (İleti Merkezi tanımsız → kod backend log'unda) tarayıcıda: kayıt → kod → panel giriş sayfasına yönlenme; yanlış kod, tekrar gönderme geri sayımı, çakışan e-posta mesajı.
- [ ] **Step 5:** Commit (vetly-site deposunda, yalnız bu task'ın dosyaları/parçaları) — `feat: 7 günlük ücretsiz deneme kayıt sayfası`

---

### Task 13: Uçtan uca senaryo ve dokümanlar

**Files:**
- Create: `frontend/e2e/trial-flow.spec.ts`
- Modify: `docs/er-diagram.mermaid` (`TRIAL_SIGNUP_REQUEST`, `TRIAL_UPGRADE_REQUEST`), `docs/implementation-plan.md` (bu turun kaydı), `docs/deployment.md` (`VETLY_TRIAL_*` değişkenleri; canlıda `ILETI_MERKEZI_API_KEY`/`ILETI_MERKEZI_HASH` tanımlı değilse kimse kayıt olamaz notu), `docs/screen-priorities.md` (`Subscription Plan Selection` satırı)
- Modify: uygulama açılışında İleti Merkezi tanımsızsa `WARN` log'u (`IletiMerkeziTrialSmsAdapter` içinde `@PostConstruct`)

**Interfaces:**
- Consumes: önceki tüm task'lar.

- [ ] **Step 1:** Playwright senaryosu (mevcut `golden-path.spec.ts` kurulumu; izole backend + veritabanı, simüle ödeme ve sabit doğrulama kodu): API ile deneme kaydı → panelde giriş → bant görünür → veritabanında `renews_at` geçmişe çekilir → sayfa yenilenir → `/uyelik` ve uyarı metni → paket seçilir → simüle ödeme → `/ayarlar/abonelik?odeme=basarili` → `/hastalar` açılır.
- [ ] **Step 2:** `cd frontend && npm run test:e2e -- trial-flow` → PASS.
- [ ] **Step 3:** Dokümanları güncelle (kısa maddeler, mevcut biçim).
- [ ] **Step 4:** Tam doğrulama: `cd backend && ./mvnw test`; `cd frontend && npm run build && npm test`; `cd /c/Users/Furkan/Desktop/vetly-site && npm run build` → hepsi PASS.
- [ ] **Step 5:** `graphify update .`
- [ ] **Step 6:** Commit — `test: deneme akışı uçtan uca senaryosu ve doküman güncellemeleri`
