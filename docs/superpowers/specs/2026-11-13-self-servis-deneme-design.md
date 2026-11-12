# Self-Servis 7 Günlük Deneme — Tasarım

**Tarih:** 2026-11-13
**Durum:** Onay bekliyor
**İlgili modüller:** `modules/platformadmin`, `modules/tenant`, `platform/` (yeni erişim kapısı), `frontend` (panel), `vetly-site` (ayrı proje: `C:\Users\Furkan\Desktop\vetly-site`)
**Önceki ilgili tasarım:** `2026-08-30-vetly-signup-odeme-design.md` (ödemeli kayıt — bu tasarım onun yanına eklenir, onu değiştirmez)

Bu belgede **[Karar]** kullanıcının verdiği kararı, **[Varsayım]** tasarımı yazanın seçimini gösterir. Varsayımlar kullanıcıya özetlenip itiraz edilmemiştir ama okurken tekrar gözden geçirilmelidir.

## 1. Amaç ve gerekçe

Bugün bir veteriner Vetly'yi ancak iki yolla kullanabiliyor: siteden ödeme yaparak ya da platform admin'in elle hesap açmasıyla. Rakipler (BulutVet, Kolayvet) ödeme istemeden 14 günlük deneme veriyor. Hedef, veterinerin siteden kendi başına, ödeme bilgisi girmeden 7 günlük deneme başlatabilmesi ve süre bitince panelin içinden paket seçip ödeyerek kesintisiz üyeliğe geçebilmesi.

Kodda deneme altyapısının yarısı var (`Subscription.startTrial`, `TRIAL` plan kodu, deneme kotaları) ama iki boşluk bu özelliği bugün kullanılamaz kılıyor: deneme süresi dolunca hiçbir şey olmuyor ve denemeden ücretliye geçiş yolu yok.

## 2. Hedefler, hedef dışı olanlar, kaçınılacaklar

**Hedefler**
- Ziyaretçi siteden formu doldurup telefonunu SMS koduyla doğrulayarak, kimsenin onayını beklemeden deneme hesabı açar. **[Karar]**
- Deneme 7 gün sürer. **[Karar]**
- Süre dolunca giriş yapılabilir ama yalnızca ödeme sayfası görünür; sayfada "Deneme kullanım süreniz dolmuştur, lütfen üyelik başlatınız" yazar, kullanıcı paket seçip öder ve üyeliği başlar. **[Karar]**
- Süre dolmadan da paket seçilip ödenebilir. **[Varsayım]**
- Aynı kişi tekrar tekrar deneme açamaz. **[Varsayım]**

**Hedef dışı**
- Belge yükleme, platform admin onayı, oda sicil numarasının doğrulanması. Araştırmada sorgulanabilir açık bir resmi kaynak bulunamadı; numara yalnızca kaydedilir. **[Karar]**
- Otomatik kart çekimi / kart saklama. Yenileme mevcut fatura akışıyla devam eder.
- Ücretli paketler arası geçiş (yükseltme/düşürme). Yalnızca TRIAL → ücretli geçiş kapsamda.
- Ödeme yapmayan kliniğin verisinin silinmesi. Veri kilitli bekler. **[Varsayım]**

**Kaçınılacaklar (teknik olarak çalışsa bile başarısızlık sayılır)**
- Kilidin yalnızca arayüzde olması: süresi dolmuş hesap API'yi doğrudan çağırarak kullanmaya devam edebilmemeli.
- Yarım kalmış bir ödeme denemesinin kliniği "ödeme gecikti" durumuna düşürüp askıya aldırması.
- Kimlik bilgisi tanımlı olmayan ortamda (CI, yeni geliştirici makinesi) uygulamanın açılmaması — sağlayıcı entegrasyonu kuralı gereği simüle davranışa düşülür.
- Ödemeli kayıt akışının (`/api/v1/public/signup/checkout`) davranışının değişmesi.

## 3. Kullanıcı akışları

### 3.1 Kayıt (vetly-site)

1. Sitede yeni `/deneme` sayfası. Hero ve Header'daki ana buton "7 Gün Ücretsiz Dene" olur ve buraya gider; mevcut ödemeli form (`#iletisim`) yerinde kalır. **[Varsayım]**
2. Form alanları **[Karar]**: ad soyad, telefon, e-posta, klinik adı, il, oda sicil numarası. Ek olarak şifre ve "Kullanım Şartları ile KVKK Aydınlatma Metni'ni okudum, kabul ediyorum" onay kutusu (mevcut `/kullanim-sartlari` ve `/gizlilik-politikasi` sayfalarına bağlanır). **[Varsayım]**
3. Gönderince telefona 6 haneli kod gider; sayfa kod giriş adımına geçer ("Kodu tekrar gönder" 60 sn sonra aktifleşir).
4. Kod doğrulanınca klinik + ilk şube + yönetici hesabı oluşur, 7 günlük deneme başlar, hoş geldiniz SMS'i ve e-postası gider. **[Karar: yeni kayıt olana SMS gitsin]**
5. Tarayıcı panelin giriş sayfasına yönlenir (`{panel}/login?email=...&deneme=1`); e-posta dolu gelir, üstte "Hesabınız oluşturuldu, şifrenizle giriş yapın" yazar. Site ile panel farklı alan adlarında olduğu için otomatik oturum açma kapsam dışı. **[Varsayım]**

### 3.2 Deneme süresince (panel)

- Panelin üstünde tüm sayfalarda bant: "Denemenizin bitmesine X gün kaldı" + ADMIN için "Üyelik Başlat" bağlantısı. **[Varsayım]**
- Ayarlar > Abonelik'te deneme kullanıcısına "Bizimle İletişime Geçin" yerine paket seçici gösterilir.
- Bitişten 2 gün önce ve son gün, klinik yöneticisine SMS + e-posta hatırlatması gider. **[Varsayım]**
- Deneme kotaları aynen kalır (`vetly.quota.trial`: 1 şube, 3 personel, 30 AI, 100 mesaj); tüm modüller açık. **[Varsayım]**

### 3.3 Süre dolunca

- Giriş çalışır. Panel rotalarının hepsi `/uyelik` sayfasına yönlenir (kenar çubuğu yok, yalnızca çıkış bağlantısı).
- ADMIN: uyarı metni **[Karar: metin aynen]** + paket kartları (ad, fiyat, özellikler, aylık/yıllık seçimi, kupon alanı) → "Bu Paketi Seç" → iyzico ödeme ekranı → dönüşte panel açılır.
- ADMIN olmayan personel: "Deneme süresi doldu, klinik yöneticinizle iletişime geçin." **[Varsayım]**
- Kilitliyken: hasta sahiplerine otomatik hatırlatma SMS'leri gitmez, kliniğin herkese açık sitesi/online randevu formu ve TARBİL eklentisi anahtarı çalışmaz. **[Varsayım]**

### 3.4 Ödeme sonrası

Üyelik ödeme günü başlar; seçilen döneme göre (aylık/yıllık) yenileme tarihi atanır. Erken ödeyen, kalan deneme günlerini kaybeder. İlk dönem için ödenmiş bir fatura kaydı oluşur (Faturalar listesi ilk günden doğru görünür). Deneme sırasında girilen tüm veri yerinde kalır. **[Varsayım]**

## 4. Backend tasarımı

### 4.1 Deneme kaydı — `modules/platformadmin`

Ödemeli kaydın (`TenantSignupRequest`) yanına, aynı desenle yeni bir entity: **`TrialSignupRequest`** (kiracıya ait değil, `platformadmin` modülünde).

| Alan | Not |
|---|---|
| `fullName`, `email`, `phone`, `clinicName`, `city`, `chamberRegistrationNo` | Formdan. Telefon E.164'e normalize edilerek saklanır. |
| `passwordHash` | Şifre düz metin tutulmaz; kayıt anında hash'lenir. |
| `otpHash`, `otpExpiresAt`, `otpAttempts`, `otpSendCount`, `lastOtpSentAt` | Kod da hash'li saklanır. |
| `status` | `PENDING` → `COMPLETED` (ya da süre aşımıyla `EXPIRED`) |
| `tenantId` | Tamamlanınca dolar. |
| `consentAcceptedAt`, `consentIp` | Onay kutusunun denetim izi. |
| `createdAt`, `completedAt` | |

Uç noktalar (`PublicSignupController`, `/api/v1/public/signup/trial/...`, mevcut `permitAll` deseni):

| Uç | Davranış |
|---|---|
| `POST /start` | Doğrular, kaydı `PENDING` oluşturur, kodu SMS ile gönderir → `{ requestId, resendAvailableInSeconds }` |
| `POST /verify` | `{ requestId, code }` → başarılıysa kiracıyı oluşturur → `{ loginUrl }` |
| `POST /resend` | `{ requestId }` → yeni kod |

Kurallar **[Varsayım — sayılar yapılandırılabilir, `vetly.trial.*`]**:
- Kod 6 hane, 5 dk geçerli, en fazla 5 yanlış deneme (sonra istek geçersiz, baştan başlanır).
- Tekrar gönderme en erken 60 sn sonra, istek başına en fazla 3 SMS.
- Aynı telefona günde en fazla 3 kayıt denemesi. IP bazlı sınır için mevcut `RateLimitFilter`'ın public havuzu kullanılır; `/trial/start` ve `/trial/resend` için daha sıkı ayrı bir havuz eklenir (her SMS maliyetli).
- **Tekrar kullanım engeli:** e-posta zaten kayıtlıysa (mevcut `TenantAdminPort.isEmailRegistered`) ya da aynı telefonla daha önce `COMPLETED` bir deneme varsa 409. Yanıt mesajı hangi alanın çakıştığını söyler; ödemeli kayıt da aynı bilgiyi verdiği için ek bir bilgi sızıntısı oluşturmaz.
- Kiracı oluşturma: mevcut `TenantAdminPort.createTenant(...)` yeniden kullanılır, ancak şifre zaten hash'li olduğu için hash kabul eden bir varyant eklenir (düz şifreyi iki istek arasında saklamamak için). `taxNumber` ödemeli akıştaki gibi `"-"`, adres `"-"`, şehir formdaki il.
- Oda sicil numarası `TrialSignupRequest`'te tutulur ve yöneticinin `StaffUser.licenseNumber` alanına da yazılır. **[Varsayım]**
- `verify` idempotent: aynı istek ikinci kez doğrulanırsa kiracı tekrar oluşturulmaz.

### 4.2 Deneme süresi

`Subscription.startTrial` içindeki sabit `plusDays(14)` kaldırılır; gün sayısı `vetly.trial.days` (varsayılan 7) ayarından gelir ve hem self-servis hem platform admin'in elle açtığı denemeler için geçerlidir. **[Varsayım: admin'in açtığı denemeler de 7 gün]**

Bir deneme şu koşulda **dolmuş** sayılır: `planCode == TRIAL` ve `renewsAt < bugün` (Europe/Istanbul). Yani kayıt günü + 7 tam gün kullanılır.

### 4.3 Erişim kapısı — `platform/`

`FeatureAccessPort` / `FeatureGateInterceptor` ile aynı desen: `platform/` altında yeni bir port (`SubscriptionAccessPort`) ve onu kullanan bir interceptor; portun uygulaması `tenant` modülünde (platform bir modüle bağımlı olamaz).

- Deneme dolmuşsa, kimliği doğrulanmış kiracı isteklerinin tümü **402** ve `TRIAL_EXPIRED` hata koduyla reddedilir. İzinli yollar: `/api/v1/auth/**` ve `/api/v1/subscriptions/**`.
- Yeni uç `GET /api/v1/subscriptions/access` (tüm roller): `{ state: TRIAL | TRIAL_EXPIRED | ACTIVE | ..., trialEndsAt, daysLeft }`. Panel bant ve yönlendirme için bunu kullanır.
- İstek dışı yollar da kapanır: `TenantLookupPort`'un "aktif kiracı" sorguları (hatırlatma zamanlayıcıları, eklenti anahtarı doğrulaması) ve kliniğe ait public uçlar (`/api/v1/public/**` altındaki klinik sitesi/online randevu) dolmuş denemeyi aktif saymaz.
- `Tenant.status` deneme boyunca `TRIAL` kalır; dolunca **`SUSPENDED` yapılmaz** (o durum girişi tamamen engeller, burada giriş açık kalmalı). Durum, tarih karşılaştırmasıyla anlık hesaplanır; ayrı bir "kilitle" işi yok.
- Durum sorgusu her istekte veritabanına gitmemesi için kısa süreli önbelleklenebilir; ödeme tamamlanınca önbellek temizlenmelidir (`PlanLookup`'taki desen).

### 4.4 Denemeden ücretliye geçiş

Fatura **ödeme başarılı olunca** oluşturulur (ödemeli kayıttaki desenin aynısı). Ödeme öncesi açık fatura kesilmez; böylece yarım kalan ödeme, gecikme/askıya alma zamanlayıcısını tetikleyemez.

- Yeni entity **`TrialUpgradeRequest`** (`platformadmin`): `tenantId`, `planCode`, `billingPeriod`, `couponCode`, `chargedAmount`, `status (PENDING/COMPLETED)`.
- `GET /api/v1/subscriptions/plans`: aktif paketler (mevcut `subscriptionApi.getPlans` bunu zaten çağırıyorsa aynen kullanılır).
- `POST /api/v1/subscriptions/upgrade-checkout` (ADMIN): `{ planCode, billingPeriod, couponCode? }` → tutarı hesaplar (`Plan.priceFor` + kupon, `InitiateSignupCheckoutUseCase` ile aynı kurallar), isteği kaydeder, `PaymentGatewayPort.initializeCheckout(requestId, ...)` → `{ checkoutFormUrl }`. Yalnızca `TRIAL` plandaki kiracılar çağırabilir.
- `PublicPaymentCallbackController`'daki mevcut zincire üçüncü halka: fatura → ödemeli kayıt isteği → **deneme yükseltme isteği**. Başarılıysa:
  1. İlk dönem faturası kesilir ve `RecordPlatformPaymentUseCase` ile ödenmiş işaretlenir (`CARD_ONLINE`).
  2. Abonelik: `planCode` = seçilen paket, `billingStatus = ACTIVE`, `renewsAt` = dönem sonu, dönem tipi kaydedilir.
  3. `Tenant.status` → `ACTIVE`.
  4. Kupon kullanım sayısı artırılır, istek `COMPLETED` olur.
  5. Tarayıcı `/ayarlar/abonelik?odeme=basarili` adresine döner (mevcut başarı bandı).
- Idempotent: aynı callback tekrar gelirse ikinci fatura/ikinci plan değişikliği olmaz (`findByIdForUpdate` + durum kontrolü, mevcut desen).
- iyzico kimlik bilgisi yoksa mevcut simüle ödeme davranışı aynen çalışır.

### 4.5 Bildirimler

`PlatformBillingSmsPort` / `IletiMerkeziPlatformBillingSmsAdapter` deseniyle `platformadmin` içinde yeni bir port + tek adaptör: doğrulama kodu, hoş geldiniz, "2 gün kaldı", "bugün son gün". İleti Merkezi kimlik bilgisi yoksa adaptör simüle eder ve **kodu yalnızca log'a yazar** (geliştirme ortamında kayıt test edilebilsin diye). Canlıda kimlik bilgisi yoksa gerçek kullanıcı kayıt olamaz; uygulama açılışında bu durum için bir uyarı log'lanır.

E-posta: mevcut SMTP altyapısıyla (`SmtpPlatformBillingEmailAdapter` deseni) hoş geldiniz ve iki hatırlatma şablonu.

Hatırlatmalar `PlatformBillingScheduler`'a beşinci adım olarak eklenir: `renewsAt == bugün + 2` ve `renewsAt == bugün` olan `TRIAL` abonelikler. Zamanlayıcı günde bir kez ve advisory lock altında çalıştığı için tarih eşitliği tekrar gönderimi kendiliğinden önler.

### 4.6 Platform admin paneli

- Mevcut "Kayıt İstekleri" sayfasına deneme kayıtları da eklenir (ad, telefon, klinik, il, oda sicil no, durum, tarih).
- Klinik detayında oda sicil numarası ve deneme bitiş tarihi görünür. Şüpheli hesap mevcut "Askıya Al" ile kapatılır; yeni bir işlem eklenmez.

### 4.7 Veritabanı

Yeni Flyway migration'ları: `trial_signup_requests`, `trial_upgrade_requests`. `subscriptions` tablosunda şema değişikliği gerekmez. Mevcut kayıtlara dokunan bir veri migration'ı **yoktur** (bkz. §7 açık soru).

## 5. Frontend tasarımı

### 5.1 vetly-site

- `src/pages/TrialSignupPage.tsx` + `/deneme` rotası; `signupApi.ts`'e `startTrial` / `verifyTrial` / `resendTrialCode`.
- İki adımlı tek sayfa: form → kod girişi. Hata mesajları alan bazlı (çakışan e-posta/telefon, yanlış kod, süre dolmuş kod, çok fazla deneme).
- İl alanı 81 ilin sabit listesinden seçilir.
- Hero/Header/Pricing'deki çağrı metinleri "7 Gün Ücretsiz Dene" olarak güncellenir; SSS'ye deneme ile ilgili bir madde eklenir.
- **Dikkat:** `vetly-site`'ta commit edilmemiş değişiklikler var (`index.html`, `Faq.tsx`, `Pricing.tsx`). Bunların üzerine yazılmaz; aynı dosyalara dokunulacaksa mevcut değişiklik korunarak düzenlenir.

### 5.2 Panel (`frontend`)

- `src/api/client.ts`: 402 + `TRIAL_EXPIRED` için 401 işleyicisine benzer bir kanca; tetiklenince `/uyelik`'e yönlendirir.
- `RequireAuth`: erişim durumu `TRIAL_EXPIRED` ise `/uyelik`'e yönlendirir (`FeatureContext` ile aynı yerde, girişte bir kez yüklenen bir durum).
- Yeni `/uyelik` sayfası (`src/pages/subscription/`): `AuthLayout` benzeri sade yerleşim. Paket kartları ortak bir `PlanPicker` bileşeni; aynı bileşen Ayarlar > Abonelik'te deneme kullanıcısına da gösterilir.
- Buton kuralı: sayfada tek `primary` — önerilen (rozetli) paketin butonu `primary`, diğerleri `secondary`.
- Deneme bandı `AppShell`'de: normalde nötr ton, son 2 gün `warning` tonu. AI mavisi kullanılmaz (AI içeriği değil).
- `LoginPage`: `?deneme=1` ile gelindiğinde e-postayı doldurur ve bilgi notunu gösterir.
- Ham hex yazılmaz; yalnızca `tokens.css` değişkenleri.

## 6. Testler

Proje konvansiyonuna göre (`should_<sonuç>_when_<koşul>`):
- Use-case birim testleri: kayıt başlatma (çakışmalar, hız sınırları), kod doğrulama (yanlış/süresi dolmuş/çok deneme/idempotent), yükseltme checkout'u, callback (başarı, başarısızlık, tekrar).
- `SubscriptionTest`: yapılandırılabilir deneme süresi, dolmuşluk hesabı (sınır günleri dahil).
- Entegrasyon testi: dolmuş denemede korumalı uçların 402, izinli uçların 200 dönmesi; ödeme sonrası kilidin açılması; yarım kalan ödemenin askıya almaya yol açmaması.
- `ApplicationModulesTest` yeşil kalmalı (yeni port/adaptör modül sınırlarına uymalı).
- Frontend birim testleri: `PlanPicker`, yönlendirme, bant. Playwright: kayıt → giriş → (tarih ileri alınmış) kilit → ödeme (simüle) → açılma.

## 7. Test ekibi hesapları (süresiz, tam yetkili)

**[Karar]** Mevcut `TRIAL` hesapların hepsi test ekibine aittir, müşteri değildir. Bunlar süresiz ve tam yetkili "test hesabı" olur. Yalnızca vitrinden kayıt olanlar deneme müşterisidir.

- Yeni ayrılmış plan kodu **`INTERNAL`** (`TRIAL` gibi `plans` tablosunda satırı yoktur, kod ismen tanır). **[Varsayım: kod adı]**
- `INTERNAL` hesap: süresi dolmaz (erişim kapısı hiç kilitlemez), tüm modüller açıktır, faturalama zamanlayıcısının dışındadır (fatura kesilmez, askıya alınmaz), hatırlatma almaz, panelde deneme bandı görmez.
- Kotalar (AI, mesaj, şube, personel) sınırsızdır. **[Varsayım — "tam yetkili" ifadesinden çıkarıldı; AI kullanımı gerçek maliyet doğurur, sınır istenirse `vetly.quota.internal` ile eklenebilir]**
- **Veri migration'ı:** Yayın anında var olan tüm `TRIAL` abonelikler `INTERNAL` plana ve `Tenant.status = ACTIVE` durumuna çekilir. Migration yalnızca o an mevcut kayıtları etkiler; sonradan vitrinden açılan denemeler `TRIAL` kalır.
- Platform admin, klinik detayındaki mevcut plan alanından bir hesabı `INTERNAL` yapabilir ya da `INTERNAL`'dan çıkarabilir; yeni bir ekran eklenmez. Plan seçeneklerinde "Test hesabı (süresiz)" olarak görünür, klinik listesinde ayrı bir rozetle işaretlenir.
- Platform admin'in elle açtığı yeni klinikler yine 7 günlük `TRIAL` olarak başlar; test hesabı olacaksa admin planı `INTERNAL` yapar. **[Varsayım]**
- Platform admin "Genel Bakış" ve faturalama özetlerinde `INTERNAL` hesaplar deneme/müşteri sayılarına karışmamalı, ayrı sayılmalıdır.

## 7.1 Başlamadan önce: çalışma ağacının durumu

Bu tasarım yazılırken ana repoda commit edilmemiş, yarım görünen bir çalışma vardı: aylık/yıllık dönem desteği (`BillingPeriod.java` yeni dosya; `Subscription.java`, `TenantAdminPort.java`, `TenantAdminPortAdapter.java`, `BillableSubscription.java` değişmiş). Bu özellik aynı dosyalara dokunur ve dönem tipini kullanır. Uygulamaya o çalışma commit edildikten (ya da durumu netleştikten) sonra, onun üzerine başlanmalıdır.

## 8. Güncellenecek dokümanlar

- `docs/api-conventions.md`: 402 / `TRIAL_EXPIRED` hata kodu ve izinli yollar.
- `docs/architecture.md`: `SubscriptionAccessPort` (FeatureAccessPort ile aynı desen) kısa madde.
- `docs/er-diagram.mermaid`: iki yeni tablo.
- `docs/implementation-plan.md`: bu turun kaydı.
- `docs/deployment.md`: yeni ortam değişkenleri (`VETLY_TRIAL_*`), İleti Merkezi kimlik bilgisinin canlıda zorunlu olduğu notu.

## 9. Uygulayana bırakılanlar

Sınıf/dosya adlarının kesin hali, önbellek süresi, SMS ve e-posta metinlerinin son yazımı (kısa, Türkçe, marka adıyla), `/uyelik` sayfasının görsel ayrıntıları (tasarım sistemine uymak kaydıyla), migration numaraları.
