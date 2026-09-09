# Faz 1 (MVP) — Uygulama Planı

Bu liste, Claude Code'un sırayla üzerinden geçebileceği somut görevlerden oluşur.
Her görev bitince kutuyu işaretle. Bir görev bitmeden bir sonrakine geçme —
özellikle backend'de, sonraki modüller bir öncekinin entity'lerine bağımlı.

Referanslar: @docs/requirements.md · @docs/architecture.md · @docs/er-diagram.mermaid ·
@docs/design-system.md · @docs/screen-priorities.md

---

## 0. Proje İskeleti
- [x] **Önce kontrol et:** `frontend/` klasörü zaten var mı ve `package.json` içeriyor mu? Varsa (kullanıcı zip'ten elle yerleştirmiş demektir) ÜZERİNE YAZMA — `cd frontend && npm install && npm run dev` ile çalıştığını doğrula, sonra devam et. Yoksa veya boşsa, kullanıcıya durdur ve sor: "frontend/ klasörü boş görünüyor, vetos-frontend.zip'i oraya çıkardınız mı?" — *(frontend/ proje kökünün dışında `myvet-frontend/` olarak duruyordu, `frontend/` adıyla doğru konuma taşındı; npm install + npm run dev doğrulandı.)*
- [x] Spring Boot 3.x projesi oluştur (Java 21, Maven), `docs/architecture.md`'deki paket yapısını kur (`platform/`, `modules/`) — *(Spring Boot 3.5.16, start.spring.io artık yalnizca 4.x sunduğu için Maven Central'dan elle kuruldu.)*
- [x] PostgreSQL bağlantısı, Flyway/Liquibase migration altyapısı — *(docker-compose ile lokal Postgres, host portu 5433 — 5432 yerel bir Windows Postgres servisiyle çakışıyordu.)*
- [x] Spring Modulith bağımlılığını ekle, boş bir `ApplicationModules.verify()` testi yaz (modül sınırı ihlali olursa build kırılsın) — *(detection-strategy=explicitly-annotated; platform alt paketleri @NamedInterface ile açık.)*

## 1. Tenant & Auth Modülü
- [x] `Tenant`, `Branch`, `Subscription` entity'leri (@docs/er-diagram.mermaid referansı)
- [x] `StaffUser` entity'si + Spring Security + JWT auth
- [x] Multi-tenant context (`TenantContext`, request bazlı tenant filtreleme)
- [x] Login / Register Clinic / Clinic Setup Wizard API endpoint'leri
- [x] Frontend: Login sayfası (mevcut mockup referans alınarak React'e dökülecek), Register + Setup Wizard akışı

## 2. Patient (Hasta & Sahip) Modülü
- [x] `Owner`, `Patient`, `Species`, `Breed`, `ConsentRecord` (KVKK) entity'leri
- [x] CRUD API'ler + arama endpoint'i (hasta+sahip birleşik arama)
- [x] Frontend: "Hastalar & Sahipler" sayfası (liste + profil, mevcut mockup referans) — *(bu sayfa için hazır mockup yoktu; mevcut Login/Dashboard tasarım dilinden üretildi.)*

## 3. Appointment (Randevu) Modülü
- [x] `Appointment`, `ServiceType` entity'leri
- [x] Haftalık takvim endpoint'i, çakışma kontrolü
- [x] No-show risk skoru alanı (başlangıçta basit kural motoru, AI değil) — *(sahibin geçmiş no-show oranı; geçmiş yoksa %10 varsayılan)*
- [x] Frontend: Randevu takvimi sayfası — *(ek olarak `/api/v1/staff-users` liste uç noktası eklendi, randevu atarken hekim seçimi için gerekliydi.)*

## 4. Encounter (Klinik/SOAP) Modülü — ürünün kalbi
- [x] `Encounter` (SOAP alanları), `VaccinationRecord`, `Prescription`, `PrescriptionItem`, `DrugCatalog` entity'leri
- [x] SOAP kayıt oluşturma/güncelleme API'si
- [x] `EncounterFinalizedEvent` domain event'i (billing ve inventory bunu dinleyecek — @docs/architecture.md Bölüm 4) — *(şu an encounterId/patientId/staffUserId taşıyor; "usedItems" inventory modülüyle birlikte eklenecek.)*
- [x] Frontend: SOAP muayene sayfası (AI Scribe UI'sı hazır, backend'e bağlanacak; AI entegrasyonu Faz 2'de gerçek API çağrısına dönüşür)
- [x] **AI Scribe — iskelet + tarayıcı tabanlı dikte (Faz 2, API anahtarı olmadan yapılabilen kısım):** `modules/ai` modülü (`SoapGenerationPort` / `AiController` / `POST /api/v1/ai/soap-drafts`), Web Speech API ile gerçek (ücretsiz, tarayıcı içi) sesli dikte + transkript metni, `EncounterPage`'de "Sesle Dikte Et" + "SOAP Taslağı Oluştur" + "Alanlara Uygula" akışı, `Encounter.aiGenerated` alanı artık gerçekten set ediliyor (`PUT /soap` `aiGenerated` parametresi) ve "AI Destekli" rozetiyle gösteriliyor. *(Transkripsiyon adımı tarayıcıda gerçek çalışıyor; metni SOAP alanlarına yapılandıran adım henüz `MockSoapGenerationAdapter` — gerçek LLM API anahtarı sağlandığında sadece bu adaptör değişecek, `SoapGenerationPort` sözleşmesi ve frontend aynı kalacak — TARBİL/`MockTarbilAdapter` ile birebir aynı desen.)*

## 5. Billing (Finans) Modülü
- [x] `Invoice`, `InvoiceLine`, `Payment` entity'leri
- [x] `EncounterFinalizedEvent` dinleyicisi → otomatik charge capture — *(DRAFT fatura + 0 tutarlı otomatik kalem oluşturulur; tutar encounter'da hizmet/ürün kırılımı tutulmadığı için resepsiyonun gireceği şekilde bırakıldı.)*
- [x] **Kasa yönetimi** (günlük açılış/kapanış, nakit mutabakatı) — rakip analiziyle MVP'ye eklendi — *(er-diagram.mermaid'de yoktu; kullanıcı onayıyla basitleştirilmiş model (mutabakat/fark hesabı olmadan) eklendi ve diyagrama işlendi.)*
- [x] **Borç listesi / cari hesap** — sahip bazlı konsolide bakiye
- [x] Frontend: Finans sayfası + Kasa + Borç Listesi ekranları

## 6. Inventory (Stok) Modülü
- [x] `InventoryItem`, `StockMovement` entity'leri
- [x] `EncounterFinalizedEvent` dinleyicisi → kullanılan malzemenin otomatik stoktan düşümü — *(bunun için encounter modülüne "Kullanılan Malzeme" kaydı — `EncounterInventoryUsage` — eklendi; `EncounterFinalizedEvent` artık `usedItems` taşıyor, Modül 4'teki not burada kapatıldı.)*
- [x] Frontend: Stok sayfası — *(SOAP sayfasına da "Kullanılan Malzeme" kartı eklendi.)*

## 7. TARBİL Entegrasyonu
- [x] `integration/tarbil` modülü, `TarbilSyncPort` arayüzü + adapter — *(gerçek Bakanlık API kimlik bilgisi/dokümanı olmadığı için `MockTarbilAdapter` stub; port/adapter deseni sayesinde gerçek HTTP client'e geçiş tek sınıf değişikliği.)*
- [x] Aşı/kimliklendirme kayıtlarının asenkron bildirimi (mesaj kuyruğu ile) — *(RabbitMQ/Kafka Faz 1'de kurulu değildi; aynı asenkron/tekrar-denenebilir davranış Spring `@Async` + `TarbilSyncLog` outbox kaydıyla sağlandı — bu implementation-detail seçimi olduğu için kullanıcıya sorulmadı.)*
- [x] Frontend: Ayarlar > Entegrasyonlar sekmesinde durum gösterimi

## 8. Website & Online Randevu Widget'ı
- [x] Basit klinik tanıtım sayfası şablonu + online randevu formu (public endpoint) — *(yeni bir "website" modülü yerine, plan metninin de işaret ettiği gibi patient/appointment/tenant modüllerine `/api/v1/public/**` altında public uç noktalar eklendi.)*
- [x] Gelen talebin `source: WIDGET` olarak Appointment modülüne düşmesi — *(bunun için `assigned_staff_id` nullable yapıldı — widget talebi hekim atanmadan REQUESTED düşer, resepsiyon Randevu Takvimi'nden hekim atayıp onaylar.)*

---

**Faz 1 (MVP) tamamlandı** — 0-8 arası tüm modüller backend+frontend olarak uçtan uca doğrulandı (`./mvnw test`, gerçek Postgres'e karşı manuel API testleri, `npm run build`).

---

## Faz 1 Kapsam Dışı (Faz 2'de ele alınacak)
AI Scribe'ın gerçek model entegrasyonu (@docs/architecture.md Bölüm 3 — `AiGatewayRouter`), Pet Owner mobil app.

- [x] **Laboratuvar, Görüntüleme, Yatış (Boarding) modülleri (requirements.md 4.5/4.6)** — bu checklist'e önceden işlenmeden eklendi, retroaktif olarak not düşülüyor: `modules/lab` (`LabResult`/`LabResultItem`, dosya eki, kural tabanlı AI değerlendirmesi — bkz. üstteki madde), `modules/imaging` (`ImagingRecord`, dosya yükleme/indirme), `modules/boarding` (oda/kafes yönetimi, konaklama kaydı → `CreateBoardingStayInvoiceUseCase` ile otomatik fatura). Her üçü de `reference-module.md` şablonuna uygun (domain/application/infrastructure/api), sidebar'a bağlı, backend+frontend tam. `docs/er-diagram.mermaid`'deki `LAB_RESULT` bloğu bu güncellemeyle gerçek şemaya uyduruldu; `IMAGING_RECORD`/`BOARDING_STAY` blokları henüz doğrulanmadı (bilinen takip görevi).

- [x] **Laboratuvar sonucu AI değerlendirmesi (Faz 2)** — kural tabanlı ön-değerlendirme: `LabReferenceRangeEvaluator` + `EvaluateLabResultItemsUseCase` (`POST /api/v1/lab-results/evaluate`) referans aralığı dışı değerleri otomatik HIGH/LOW işaretleyip taslak özet oluşturuyor, API anahtarı gerektirmiyor. Frontend (`LabResultDetailModal`) tasarım sistemi §6.1'e uyacak şekilde hizalandı: buton `Button variant="ai"` (mavi aile, marka moru değil), sonuç `Badge tone="ai"` ile işaretleniyor, "hekim onayına sunulur, kaydetmeden uygulanmaz" kopyası eklendi. *(Faz 3: `AiGatewayRouter` üzerinden gerçek LLM çağrısıyla taslak "genel değerlendirme" üretimi — bilinçli olarak bu adımın kapsamı dışında bırakıldı, API anahtarı yok.)*
- [x] **WhatsApp/SMS otomasyonu (Faz 2, requirements.md 4.5/4.12) — iskelet + gerçek zamanlanmış iş:** `modules/notification` (TARBİL ile birebir aynı port/adapter + outbox deseni): `NotificationSendPort` / `MockNotificationAdapter` (gerçek sağlayıcı hesabı/API anahtarı yok — `Ayarlar > Bildirimler`'de "Mock modu" rozetiyle açıkça belirtiliyor), `NotificationLog` outbox tablosu + `@Async` executor + retry. İki gerçek tetikleyici: (1) `AppointmentScheduledEvent` dinlenerek randevu onayı anında kuyruklanıyor — curl ile uçtan uca doğrulandı; (2) her gün 09:00'da (`@Scheduled`, Europe/Istanbul) çalışan `AppointmentReminderScheduler`, tüm aktif kiracılar için yarının CONFIRMED randevularını tarayıp hatırlatma kuyruklar (`TenantLookupPort.findActiveTenantIds()` ile çok kiracılı döngü; `existsByRelatedEntityIdAndNotificationType` ile aynı randevu için tekrar gönderim engellenir). Gerçek sağlayıcı (Twilio/Netgsm/WhatsApp Business API) eklendiğinde sadece `MockNotificationAdapter` değişecek. *(Sonraki tur: Kolayvet'in Sms & Whatsapp ekranları referans alınarak — kullanıcı kendi görsellerini paylaştı — tam bir yönetim sayfası eklendi: yeni üst-seviye `/sms-whatsapp` sayfası (Gönderim Geçmişi filtrelenebilir hale getirildi — kanal/durum/tür/tarih/arama; Toplu Kampanya — Müşteriler/Aşı Takvimi/Randevular/Borçlu Müşteriler/Özel Numaralar kaynaklarından alıcı önizleyip `{musteri_adi}` gibi değişkenli mesaj gönderme; Şablonlar CRUD — yeni `message_templates` tablosu, `V19` migration; Ayarlar). `Invoice`'da olduğu gibi `notification_log.owner_id` nullable yapıldı (Özel Numaralar sahipsiz gönderim için), `OwnerSummary`'ye `smsConsent`/`whatsappConsent` eklendi ve kampanya gönderimi bu onay bayraklarına uyuyor. "Hastalar" (tür/ırk filtresi) ve "Muayeneler" (geçmiş muayene filtresi) alıcı kaynakları bilinçli olarak ertelendi — `patient`/`encounter` modüllerinde bu filtreler için tenant-geneli sorgu altyapısı yok, ayrı bir tur gerektiriyor.)*
- [x] **e-Fatura / e-Arşiv entegrasyonu (Faz 2, requirements.md 4.7) — iskelet + gerçek tetikleyici:** `modules/integration/efatura` (TARBİL ile birebir aynı port/adapter + outbox deseni): `EInvoiceGatewayPort` / `MockEInvoiceGatewayAdapter` (gerçek GİB/özel entegratör — Foriba, Uyumsoft vb. — hesabı yok; `Ayarlar > e-Fatura`'da "Mock modu" rozetiyle belirtiliyor), `EInvoiceSubmission` outbox tablosu + `@Async` executor + retry. Gerçek tetikleyici: billing modülünde yeni `InvoiceIssuedEvent` — `IssueInvoiceUseCase` artık fatura kesilirken (1) kalemlerin KDV toplamını `taxAmount`'a yazıyor (önceden hep 0'dı) ve (2) event yayınlıyor; efatura modülü bunu dinleyip gönderimi kuyruklar. Başarılı gönderimde GİB referansı dar amaçlı bir yazma portu (`InvoiceEInvoiceUpdatePort`) üzerinden `invoices.e_invoice_ref` alanına geri yazılıyor (bu alan şemada zaten vardı, hiç kullanılmıyordu) — curl ile uçtan uca doğrulandı (KDV'li ve KDV'siz kalemlerle). *(GİB mükellef sorgu servisi bağlı olmadığından tüm alıcılar bireysel kabul edilip sadece e-Arşiv kullanılıyor; gerçek sağlayıcı eklendiğinde hem `MockEInvoiceGatewayAdapter` değişecek hem de e-Fatura/e-Arşiv ayrımı için mükellef sorgusu eklenecek.)*
- [x] **Gelişmiş raporlama (Faz 2, requirements.md 4.12):** (1) Dashboard'daki "İşletme özeti" sekmesi eskiden tamamen sabit/uydurma verilerle doluydu (₺842.500 gibi sahte rakamlar) — artık gerçek `GetRevenueSummaryUseCase`/`GetAppointmentActivitySummaryUseCase`/`GetPatientGrowthSummaryUseCase` sorgularına bağlı; "AI özeti" banner'ı da kural tabanlı gerçek hesaplamaya döndü. (2) `Raporlar` sayfası: filtrelenebilir (tarih aralığı, durum) + CSV'ye (Excel uyumlu, UTF-8 BOM'lu) aktarılabilir **Ciro Raporu** ve **Ürün/Hizmet Satış Raporu**. (3) `Invoice`'a `staff_user_id` eklendi (`V18` migration, `AutoCaptureEncounterChargeUseCase`'in zaten elinde olan `EncounterFinalizedEvent.staffUserId`'yi artık faturaya işliyor) — bunun üzerine aynı filtrelenebilir/CSV desenini kullanan **Hekim Bazlı Performans Raporu** (`/reports/staff-performance`) ve `BranchLookupPort.findAllByTenantId` ile tüm şubeleri (fatura olmasa da) listeleyen **Şube Karşılaştırma Raporu** (`/reports/branch-comparison`) eklendi.
- [x] **screen-priorities.md denetimi (Faz 2 sonu):** Bu doküman uzun süredir sadece yeni eklenen (Lab/Görüntüleme/Yatış/Raporlar/Ayarlar) bölümlerde güncelleniyordu — Faz 1'de fiilen tamamlanan eski bölümler (0-5, 8-9, 14) hiç işaretlenmemiş kalmıştı. Kod tabanına karşı satır satır doğrulanıp güncellendi. Bu geçişte gerçekten eksik olduğu ortaya çıkan, önceden fark edilmemiş boşluklar (sonraki tur için not): **reçete oluşturma frontend'de hiç yok** (backend `POST /api/v1/prescriptions` hazır ama UI'da çağrılmıyor, sadece geçmiş reçete listeleme var); **ilaç etkileşim uyarısı** placeholder (`DrugCatalog.interactionFlags` kullanılmıyor); **AI SOAP Generator** uçtan uca bağlı ama backend adaptörü stub (gerçek model yok, transkripti Subjective'e kopyalıyor); **Diagnosis Selection/Treatment Plan (+AI öneri)** hiç yok, Assessment/Plan serbest metin; **manuel fatura oluşturma** endpoint'i yok (faturalar sadece encounter'dan otomatik taslak olarak açılıyor); **online ödeme entegrasyonu** yok (tüm ödemeler elle kaydediliyor); **KVKK onay UI'ı** yok (backend `ConsentRecord` sistemi tam ama frontend bağlı değil); **Owner List** bağımsız bir ekran olarak yok (sadece autocomplete); **Daily Calendar/Doctor Schedule** yok (sadece haftalık, kliniğe göre tek liste); **Medicine/Vaccine Management** ayrı bir katalog olarak yok (backend `DrugCatalog`/`DrugsController` var ama frontend'den erişilmiyor). Detaylar için screen-priorities.md'deki ilgili satırların notlarına bakınız.
- [x] **Yönetim ve Ayarlar ekranları (Faz 2, screen-priorities.md §12):** screen-priorities.md'deki 10 ekran, kullanıcı onayıyla 5 bağımsız dilime indirgendi (Kullanıcı/Personel/Hekim Profili ekranları tek "Kullanıcı Yönetimi" ekranında birleştirildi). `Ayarlar` sayfası düz sekme yapısından `NavLink` + iç içe `<Routes>` yapısına geçirildi (`/ayarlar/*`) — mevcut 5 panel davranış değişmeden taşındı. **Şube Yönetimi:** çoklu şube CRUD (`GET/POST /branches`, `GET/PUT /branches/{id}`), `GetCurrentBranchUseCase` id-parametreli olduğu için tekrar kullanıldı. **Kullanıcı Yönetimi:** `StaffUser`'a `active`/`specialty`/`bio` eklendi (`V20`), pasif hesapla girişe `LoginUseCase`'de engel kondu, rol VET seçilince uzmanlık/lisans/bio alanları aynı formda açılıyor; `GET /api/v1/staff-users` zenginleştirildi ve **bilinçli olarak** herkese-açık bırakıldı (aynı kiracı içi personel dizini, PII değil — kullanıcı onayıyla, ayrı bir ADMIN-only endpoint açılmadı). Kendi rolünü ADMIN dışına düşürme / kendini pasifleştirme `SelfAccountManagementForbiddenException` (403) ile engellendi. **Çalışma Saatleri:** yeni `BranchWorkingHours` (`V21`) ve `StaffShiftTemplate` (`V22`) entity'leri — her ikisi de tam hafta sil-ekle deseniyle yazılıyor, henüz `LookupPort`'ları yok (`appointment` modülü slot hesaplamasında kullanmak isteyince eklenecek, YAGNI). **Rol/Yetki** ve **Abonelik** ekranları bilinçli olarak salt-okunur: ilki `api-conventions.md`'deki rol matrisini statik gösterir, ikincisi mevcut `Subscription` kaydını gösterip "bizimle iletişime geçin" CTA'sı sunar — gerçek plan kataloğu/ödeme altyapısı yok. *("Clinic Settings" — Tenant seviyesinde ayrı bir marka/logo ekranı — ve ayrı bir "Billing" (fatura geçmişi) ekranı bilinçli olarak bu turun kapsamı dışında bırakıldı; screen-priorities.md'de işaretsiz bırakıldı.)*
- [x] **Platform Admin Paneli (Faz 2/3 sınırı, screen-priorities.md §13, requirements.md §3 "Süper Admin" personası):** Bu özellik için hiçbir kod yoktu (tamamen greenfield) ve mimari inceleme kritik bir gerçeği ortaya çıkardı: mevcut `JwtTokenProvider`/`AuthenticatedStaffUser`/`JwtAuthenticationFilter`/`TenantContext` dörtlüsü her zaman tam olarak bir `tenantId` olduğunu varsayıyor — platform admin ise tanımı gereği hiçbir kiracıya ait değil. Kullanıcı onayıyla **tamamen paralel bir auth yığını** kuruldu, mevcut `SecurityConfig.java`/`JwtTokenProvider.java` dosyaları **hiç değiştirilmedi**: yeni `modules/platformadmin` modülü (`PlatformAdminUser` giriş kimlik bilgisi + `Plan` katalog entity'si, tek modülde — ikisi de "kiracıya ait değil" ortak özelliğini taşıyor), `platform/security/`'de `AuthenticatedPlatformAdmin`/`PlatformAdminJwtTokenProvider` (JWT'de `"type":"platform-admin"` claim'i ile ayrıştırılıyor)/`PlatformAdminAuthenticationFilter` (TenantContext'e hiç dokunmuyor)/`PlatformAdminSecurityConfig` (`@Order(1)`, `/api/v1/platform-admin/**`'e `securityMatcher` ile izole ikinci bir `SecurityFilterChain`). Tek platform admin hesabı, uygulama açılışında `PlatformAdminBootstrapRunner` (`ApplicationRunner`) ile `PLATFORM_ADMIN_EMAIL`/`PASSWORD` env var'larından otomatik oluşturuluyor (davet/çoklu-admin yönetimi kapsam dışı). **Kenar durum (mimari istisna, bkz. architecture.md §6.1):** `modules/tenant/domain/TenantAdminPort.java` — normal `*LookupPort`'lardan farklı olarak yazma da içeriyor (platform admin'in herhangi bir kiracının aboneliğini/durumunu değiştirebilmesi için), sadece `modules.platformadmin` kullanır. **Tenant Listesi/Detay:** ucuz kullanım metrikleri (şube/personel sayısı — `StaffUserJpaRepository.countByBranchIdIn` yeni metodu) — hasta/randevu sayısı gibi diğer modüllere ait metrikler kapsam dışı bırakıldı (yeni cross-module port gerektirirdi). **Plan Kataloğu:** `Plan.code` ile `Subscription.planCode` arasında bilinçli olarak FK yok (zaten free-text bir alan) — tenant detayındaki "plan değiştir" formu bu yüzden sıkı bir `<select>` değil, datalist destekli serbest giriş. **Platform Faturalama:** gerçek bir ödeme tahsilat sistemi yok, sahte fatura kaydı uydurulmadı — sayfa zaten çekilen tenant listesini `billingStatus`'e göre gruplayan salt-okunur bir özet (backend değişikliği yok). Backend curl testi (kayıt/giriş regresyon dahil) + tarayıcıda uçtan uca (plan oluştur → tenant'a ata → askıya al/aktif et) doğrulandı.
- [x] **Reçete Oluşturma (Faz 1/2 boşluğu, screen-priorities.md §5):** Backend zaten tam hazırdı (`POST /api/v1/prescriptions`, `IssuePrescriptionUseCase`) ama hiçbir frontend ekranı çağırmıyordu — sadece geçmiş reçeteleri salt-okunur listeleme vardı. Backend'e **hiç dokunulmadan** sadece frontend eklendi: `EncounterPage.tsx`'e (Prescription, `encounterId` zorunlu FK olduğu için sadece bir muayene içinden oluşturulabiliyor — bağımsız/patient-only değil) `LabResultDetailModal.tsx`'in çoklu-satır ekleme desenini izleyen yeni bir `PrescriptionCard.tsx` eklendi: birden fazla ilaç satırı (ilaç/dozaj/sıklık/süre/uygulama yolu) tek seferde gönderiliyor, `controlledSubstance` bayrağı seçilen ilaçlardan otomatik türetiliyor (kontrollü bir ilaç seçilirse elle işaretlemeye gerek yok). Aynı kart, o muayeneye ait reçeteleri de listeliyor (hasta bazlı tüm reçeteler içinden `encounterId` ile client-side filtrelenerek — `OwnerDetailPage`'in fatura filtreleme deseniyle aynı). **İlaç etkileşim kontrolü bilinçli olarak bu turun kapsamı dışında bırakıldı:** `DrugCatalog.interactionFlags` hiçbir ilaç için doldurulmamış (tamamen boş) — kullanıcı onayıyla, gerçek bir farmakolojik referans kaynağı bulunmadan sahte/uydurma etkileşim verisi kodlanmadı; lab modülündeki "kural tabanlı ön-değerlendirme" deseni burada bilerek uygulanmadı. Tarayıcıda uçtan uca doğrulandı (çok ilaçlı reçete oluşturma, kontrollü ilaç rozetinin otomatik tetiklenmesi, hasta sayfasındaki Reçeteler sekmesinde de doğru görünmesi).
- [x] **İlaç Etkileşim Kontrolü (Faz 2, önceki turun boşluğu):** Kullanıcı onayıyla veri kaynağı netleştirildi — sistem hangi ilaçların etkileştiğini kendisi uydurmaz, sadece klinik/hekim tarafından girilen veriyi çapraz kontrol eder (`LabReferenceRangeEvaluator` ile birebir aynı "kural tabanlı, veri girişi kullanıcıda" deseni). `DrugCatalog.interactionFlags`'in formatı netleştirildi: virgülle ayrılmış `drug_catalog.id` listesi (önceki yorumdaki "ilaç adı" varsayımı yerine — isimle eşleştirme kırılgan olurdu). Yeni `DrugInteractionEvaluator` (domain, framework-agnostic) seçilen ilaçlar arasında iki yönlü (A→B veya B→A işaretliyse) eşleşme arar. Backend: `UpdateDrugCatalogUseCase` (+ `PUT /api/v1/drugs/{id}`, ADMIN-only, mevcut `POST` ile aynı yetki), `CheckDrugInteractionsUseCase` (+ `POST /api/v1/drugs/check-interactions`, VET/ADMIN — lab'daki `EvaluateLabResultItemsUseCase` gibi stateless/kayıt yapmayan bir kontrol). Frontend: yeni `Ayarlar > İlaç Kataloğu` sekmesi (`DrugCatalogPanel.tsx`, `StaffManagementPanel`'in modal+tablo desenini izliyor) — ilaç oluşturma/düzenleme ve "bu ilaç şunlarla etkileşir" çoklu seçimi; `PrescriptionCard.tsx`'te seçilen ilaçlar değiştikçe (2+ ilaç olduğunda) arka planda `check-interactions` çağrılıp sonuç varsa `warning` tonunda (rust/terrakota — `ai` değil, çünkü AI üretimi değil, kullanıcı verisine dayalı bir risk bildirimi) uyarı banner'ı gösteriliyor; **bloklayıcı değil** — lab modülündeki "hekim onayına sunulur, kaydetmeden uygulanmaz" ilkesiyle aynı, hekim gördükten sonra yine de reçeteyi oluşturabiliyor. Backend curl ile uçtan uca doğrulandı (etkileşim işaretleme, iki yönlü tespit, ilgisiz ilaç çiftlerinde uyarı çıkmaması); frontend `tsc`/`vite build` temiz, tarayıcıda görsel doğrulama bu turda yapılamadı (Chrome eklentisi bağlı değildi) — kullanıcının `npm run dev` ile bir sonraki fırsatta gözden geçirmesi önerilir.
- [x] **KVKK Rıza Yönetimi (Faz 2 boşluğu, screen-priorities.md §3, `/superpowers:brainstorming` ile tasarlandı):** Backend zaten tam hazırdı (`ConsentRecord`/`ConsentType` + `RecordConsentUseCase`/`RevokeConsentUseCase`/`ListConsentsUseCase`, `POST/GET /owners/{id}/consents`, `POST /owners/{id}/consents/{consentId}/revoke`) ama hiçbir frontend ekranı çağırmıyordu — sadece `Owner.smsConsent`/`whatsappConsent`/`notificationConsent`/`marketingConsent` gibi ayrı, basit kampanya-filtresi bayrakları vardı (bunlara **hiç dokunulmadı**, iki sistem bilinçli olarak birbirinden bağımsız bırakıldı). İki parça eklendi: (1) `NewOwnerPage.tsx`'e diğer checkbox'lardan görsel olarak ayrık (`warning` çerçeveli kutu), **zorunlu** bir "KVKK Aydınlatma Metni'ni okudum, açık rızam vardır" checkbox'ı — sahip oluşturulur oluşturulmaz otomatik olarak `KVKK_ACIK_RIZA` için bir `ConsentRecord` yazılıyor (kayıt anı = KVKK'nın gerektirdiği "ilk veri toplama anı", kullanıcıyla karar verildi). (2) `OwnerDetailPage`'e üçüncü "KVKK" sekmesi (`ConsentTab.tsx`): 3 onay türü (KVKK Açık Rıza/Pazarlama/Veri Aktarımı) için güncel durum + Ver/Geri Çek + tam kronolojik geçmiş — bu özellikten önce oluşturulmuş sahiplerde geçmiş boş görünür, resepsiyon isterse geriye dönük ekleyebilir (migration/backfill gerekmedi). Küçük bir backend eki: `ConsentRecordSummary`/`ConsentRecordResponse`'a entity'de zaten var olan ama hiç taşınmayan `ipAddress` alanı eklendi (denetim izinin asıl değeri budur). Backend curl ile uçtan uca doğrulandı (grant→revoke→yeniden grant geçmişi, aynı sahipte birden fazla onay türü, IP doğru yakalanıyor); frontend `tsc`/`vite build` temiz, tarayıcıda görsel doğrulama yapılamadı (Chrome eklentisi bağlı değil).
- [x] **SMS/WhatsApp: sol navbar'dan Ayarlar altına taşındı (UX kararı, kullanıcı onayıyla):** Kullanıcı rakip veteriner uygulamalarındaki yerleşimi referans göstererek bunun bağımsız bir üst-menü öğesi değil, Ayarlar sekmesi olması gerektiğine karar verdi. `SmsWhatsappPage.tsx` (kendi `AppShell`/başlık/sekme çubuğuna sahipti) kaldırıldı; içerdiği 4 alt-sekme (`HistoryTab`/`CampaignTab`/`TemplatesTab`/`SettingsTab`, `src/pages/sms-whatsapp/` altında kalmaya devam ediyor — sadece taşıyıcı sayfa silindi) artık `Ayarlar > SMS / WhatsApp` sekmesindeki yeni `SmsWhatsappPanel.tsx`'in içinde, kendi iç sekme çubuğuyla gösteriliyor. Dış Ayarlar sekmeleriyle (`SettingsPage.module.css`'teki alt-çizgili stil) görsel karışmasın diye iç sekme çubuğuna bilinçli olarak farklı bir stil (pill/segmented, `--color-cream` zemin) verildi — aynı sayfada iki adet birebir aynı görünen sekme çubuğu üst üste durmasın diye. Eskiden salt bir yönlendirme kartından ibaret olan `Ayarlar > Bildirimler` (`NotificationsPanel.tsx`) sekmesi bu taşımayla anlamsızlaştığı için tamamen kaldırıldı — SMS/WhatsApp zaten Ayarlar'da. Eski `/sms-whatsapp` rotası `/ayarlar/sms-whatsapp`'a yönlendiriliyor (olası yer imi/alışkanlık kırılmasın diye).

## Faz 2 — Kapsamlı Panel Denetimi Sonrası Tur (Faz 1 Kapsam Dışı bölümünün gözden geçirilmesiyle önceliklendirildi)

Bu tur, backend/frontend/docker'ın uçtan uca denetlenip screen-priorities.md'nin koda karşı doğrulanmasıyla başladı; kullanıcı onayıyla "gerçek ürün boşlukları" (A grubu) önceliklendirildi, tanı/tedavi planı AI önerisi ve online ödeme (gerçek sağlayıcı hesabı olmadığı için) bilinçli olarak ertelendi.

- [x] **Owner List bağımsız ekranı:** `/musteriler` — `OwnersListPage.tsx`, "Hastalar & Sahipler" sayfasının yeni "Sahipler" sekmesi (`PatientsPage`'e de aynı sekme eklendi, sidebar'a yeni bir öğe eklenmedi — tek "Hastalar & Sahipler" girişi her iki listeyi de kapsıyor). Mevcut `GET /api/v1/owners?query=` (boş sorgu tüm sahipleri döndürüyor) aynen kullanıldı, backend değişikliği gerekmedi.
- [x] **Manuel fatura oluşturma:** `CreateManualInvoiceUseCase` + `POST /api/v1/invoices` (owner seçilip boş bir DRAFT fatura açıyor, `AutoCaptureEncounterChargeUseCase`/`CreateBoardingStayInvoiceUseCase` ile aynı desen) — Finans > Faturalar'a "Yeni Fatura" butonu + `NewInvoiceModal.tsx` (owner-picker, `NewBoardingStayPage`'deki autocomplete deseniyle aynı) eklendi; oluşturulan taslak doğrudan mevcut `InvoiceDetailModal`'da açılıp kalem ekleme/kesme akışına devam ediyor.
- [x] **Rol bazlı Dashboard dallanması:** `/invoices/**` sadece RECEPTIONIST/ADMIN'e açık olduğu için "İşletme özeti" sekmesi (backend'e 403 atan `billingApi.revenueSummary()` çağrısına bağlı) artık sadece bu iki role gösteriliyor; VET girişinde varsayılan "Bana atanan" filtresi açık, TECHNICIAN girişinde varsayılan sekme "Bekleme Salonu". ADMIN girişinde varsayılan görünüm "İşletme özeti".
- [x] **Daily Calendar / Doctor Schedule:** Randevu Takvimi'ne "Haftalık / Günlük / Hekim" sekmesi eklendi — aynı `weeklyCalendar` verisi client-side güne veya hekime (VET rollü `staff-users`) göre filtreleniyor/gruplanıyor; üstte ayrıca her iki modu etkileyen bir "Hekim" seçici var. Backend değişikliği gerekmedi.
- [x] **Medicine Management (+ AI Merkezi) — yeniden konumlandırma (kullanıcı geri bildirimiyle, aynı tur içinde):** İlk halde `DrugCatalogPanel` Ayarlar'dan çıkarılıp `/ilac-katalogu` bağımsız sayfası, AI yetenekleri de `/ai-merkezi` bağımsız sayfası yapılmıştı (sidebar'da ikişer yeni öğe). Kullanıcı "AI Merkezi ne işe yarıyor, ilaç kataloğu Ayarlar'da olmalı değil mi" diye sorunca gerekçe zayıf çıktı: İlaç Kataloğu, Tür/Irk ve Hizmetler gibi diğer tüm referans-veri katalog ekranlarıyla aynı desende (nadir kullanılan admin CRUD'u) — ayrı sidebar girdisini hak etmiyor. AI Merkezi de kendi başına iş yapmayan, sadece ilgili ekrana yönlendiren bir "rehber" sayfası — günlük kullanılan ekranlarla aynı seviyede sidebar'da durmasının değeri yok. İkisi de **Ayarlar'a geri taşındı** (`AiCenterPanel.tsx`, `DrugCatalogPanel.tsx` — ikisi de `pages/settings/` altında, `SETTINGS_TABS`'a birer satır), sidebar'daki `ilac-katalogu`/`ai-merkezi` girdileri ve `/ilac-katalogu`/`/ai-merkezi` top-level route'ları kaldırıldı. `AiCenterPanel` içindeki "İlaç Kataloğu'nu yönet" linki `/ayarlar/ilac-katalogu`'na güncellendi. *(Vaccine Management için ayrı bir katalog hâlâ oluşturulmadı — veri modelinde `vaccineName` serbest metin, mevcut "Aşı Takvimi" sayfası zaten aşı kayıtlarını yönetiyor, kullanıcıya sorulmadan yeni bir tıbbi referans veri modeli uydurulmadı.)*
- [x] **Invite Team Members (mock/iskelet):** TARBİL/e-Fatura ile birebir aynı port/adapter deseni — yeni `StaffInvite` entity'si (`V24` migration) + `InviteEmailPort`/`MockInviteEmailAdapter` (gerçek e-posta sağlayıcısı yok, davet linki sunucu loglarına yazılıyor, kullanıcı onayıyla mock bırakıldı). Backend: `InviteStaffMemberUseCase`/`ListStaffInvitesUseCase`/`RevokeStaffInviteUseCase` (ADMIN, `/api/v1/staff-invites`) + `GetStaffInviteByTokenUseCase`/`AcceptStaffInviteUseCase` (public, `/api/v1/public/staff-invites/{token}`, `PublicClinicController` ile aynı `permitAll` deseni). Frontend: Ayarlar > Kullanıcılar'da "Ekip Üyesi Davet Et" + Bekleyen Davetler listesi (iptal edilebilir), yeni bağımsız `/davet/:token` sayfası (`AcceptInvitePage.tsx`, `AuthLayout`/`RegisterClinicPage` deseniyle) — şifre belirlenince hesap oluşturulup otomatik giriş yapılıyor. Curl ile uçtan uca doğrulandı (davet oluşturma, çakışan e-posta 409, kabul→giriş, süresi geçmiş/iptal edilmiş/kabul edilmiş davetin tekrar kullanılamaması 422, ADMIN olmayan rolün 403 alması).
- [x] **Yan bulgu — global yetkilendirme hatası düzeltmesi:** Invite Team Members'ı test ederken `@PreAuthorize` reddinin `GlobalExceptionHandler`'da yakalanmayıp 403 yerine 500 döndüğü keşfedildi (Spring Security 6.3+'ta `AccessDeniedException`'ın alt sınıfı `AuthorizationDeniedException`'a geçiş, eski handler'ı es geçiyordu) — bu, **sadece bu turdaki özelliğe değil, rol kısıtlı TÜM endpoint'lere** (örn. `/invoices/**`'e VET erişimi) etkiyen önceden var olan bir hataydı. `GlobalExceptionHandler`'a `AccessDeniedException` handler'ı eklenerek api-conventions.md'nin belgelediği 403 sözleşmesi geri kazanıldı; curl ile hem eski hem yeni davranış karşılaştırmalı doğrulandı.
- [x] **Uçtan uca kapsamlı smoke test turu (TARBİL/SMS-WhatsApp/e-Fatura dahil "her senaryo" denetimi):** `backend/smoke_test.sh` yazıldı — gerçek Postgres'e karşı 17 bölüm, 74 senaryo (klinik kaydı, hasta/sahip, randevu→bildirim tetikleme, muayene→otomatik fatura+stok düşümü, manuel+otomatik fatura→e-Fatura tetikleme, kasa, TARBİL sync (aşı+kimlik), lab/görüntüleme/yatış, reçete+ilaç etkileşimi, raporlama, SMS/WhatsApp kampanya+şablon, davet akışı, rol/yetki sınırları, platform admin, public website/widget). İlk koşuda 2 gerçek, önceden var olan hata bulundu ve düzeltildi:
  1. **`GET /owners/campaign-candidates` (SMS/WhatsApp kampanyasının "Müşteriler" alıcı kaynağı) tarih filtresi verilmediğinde her zaman 500 dönüyordu** — `OwnerJpaRepository.findByTenantIdWithFilters`'daki native SQL sorgusunda `NULL` bağlanan `:registeredFrom`/`:registeredTo` parametrelerinin tipini PostgreSQL JDBC sürücüsü çıkaramıyordu (`could not determine data type of parameter $4`). Parametrelere `CAST(... AS timestamptz)`/`CAST(... AS text)` eklenerek düzeltildi.
  2. **Bozuk/parse edilemeyen istek gövdesi (örn. geçersiz tarih formatı) 400 yerine 500 dönüyordu** — `GlobalExceptionHandler`'a `HttpMessageNotReadableException` handler'ı eklendi (api-conventions.md'deki 400 sözleşmesiyle uyumlu hale getirildi).
  Script tekrar çalıştırılabilir (`RUNID=$(date +%s)` ile her koşuda benzersiz e-posta üretir, sabit veri çakışması olmaz) — ileride regresyon kontrolü için yeniden kullanılabilir.
- [x] **Kullanıcı listesi PII sızıntısı düzeltmesi + Ayarlar navigasyon hatası (kullanıcı geri bildirimi):** İki ayrı sorun bildirildi: (1) sol navbar altındaki kullanıcı avatarına tıklayınca Ayarlar'ın Entegrasyonlar sekmesine gidiyordu, Kullanıcılar sekmesine gitmesi bekleniyordu — `SettingsPage.tsx`'teki index route'un yönlendirme hedefi düzeltildi. (2) `GET /api/v1/staff-users` — e-posta/telefon/lisans no/uzmanlık/bio içeren tam personel detayı — herhangi bir role açıktı (`implementation-plan.md`'deki önceki bir turda "PII değil" gerekçesiyle bilinçli bırakılmıştı, ama bu uçnokta aynı zamanda randevu atama dropdown'u/hekim filtresi için TÜM rollerce kullanılıyordu — yani RECEPTIONIST/TECHNICIAN/VET her randevu ekranını açtığında tüm personelin PII'sini de indiriyordu). Çözüm: mevcut `GET /api/v1/staff-users` `@PreAuthorize("hasRole('ADMIN')")` ile kilitlendi; yeni `GET /api/v1/staff-users/directory` (PII taşımayan `StaffDirectoryResponse` — id/ad/rol/aktif) tüm rollere açık bırakıldı. `appointmentApi.listStaff()` yeni `/directory` uç noktasına yönlendirildi (frontend tipi zaten sadece bu alanları kullanıyordu, değişiklik gerekmedi). `SettingsPage.tsx`'te `SETTINGS_TABS`'a `adminOnly` bayrağı eklendi — Kullanıcılar sekmesi sıradan rollere sekme çubuğunda görünmüyor, URL'ye direkt gidilirse "yetkiniz yok" mesajı gösteriliyor. `WorkingHoursPanel` (zaten shift uç noktaları ADMIN-only olduğu için pratikte zaten sadece ADMIN'e işlevseldi) artık personel listesini de yükleyemiyor sıradan rollerde — mevcut hata yakalama (`.catch`) bunu zaten düzgün karşılıyor, yeni bir regresyon değil. `./mvnw test` yeşil, `tsc`/`vite build` temiz; ayrı bir portta (8081) gerçek Postgres'e karşı curl ile doğrulandı: ADMIN → tam liste 200, RECEPTIONIST → tam liste 403, RECEPTIONIST → `/directory` 200 (PII yok).
- [x] **Patient Health Summary / Clinical Timeline (Faz 2, screen-priorities.md §2) — tamamen frontend, backend değişikliği yok:** `PatientDetailPage`'de mevcut 5 sekmenin (Muayene/Aşı/Reçete/Lab/Görüntüleme) verisi zaten sayfa yüklenirken tek seferde çekiliyordu — yeni bir "Genel Bakış" sekmesi (varsayılan/ilk sekme) bu veriyi backend'e hiç dokunmadan istemci tarafında birleştirdi. Yeni `patientTimeline.ts` (saf fonksiyonlar, component'ten ayrı): `buildPatientTimeline` 5 kaynağı (`encounters`/`vaccinations`/`prescriptions`/`labResults`/`imagingRecords`) tek tarihe göre azalan sıralı bir listeye birleştirir (lab/görüntüleme için `resultedAt ?? requestedAt`), `buildWeightTrend` encounter'lardaki `weightKg` değerlerinden artan sıralı bir trend dizisi çıkarır. UI: Sağlık Özeti kartı (son muayenenin vital özeti + kilo trendi — mevcut bağımsız `LineChart` bileşeni yeniden kullanıldı, yeni bağımlılık eklenmedi, <2 veri noktasında "yeterli veri yok" notu) ve Zaman Çizelgesi kartı (tür etiketi + özet + mevcut durum badge'leri; muayene/lab/görüntüleme satırları tıklanınca mevcut sekmelerdeki gibi SOAP sayfasına gider/modal açar, aşı/reçete satırları mevcut sekmelerdeki gibi tıklanamaz). **Aktif problem listesi (kronik hastalık etiketleri) kullanıcı onayıyla bilinçli olarak kapsam dışı bırakıldı** — mevcut veri modelinde (Assessment hâlâ serbest metin) karşılığı yok, yeni bir domain kavramı + entity/migration gerektirirdi. `tsc`/`vite build` temiz; tarayıcıda görsel doğrulama yapılamadı (kullanıcının zaten çalışan `npm run dev` oturumuna dokunulmadı) — kullanıcının bir sonraki fırsatta gözden geçirmesi önerilir.
- [x] **Physical Examination Form (Faz 2, screen-priorities.md §5):** `Encounter`'a sabit 10 vücut sistemi listesi (Genel Görünüm, Deri/Kürk, Göz-Kulak-Ağız, Kardiyovasküler, Solunum, Gastrointestinal, Ürogenital, Kas-İskelet, Nörolojik, Lenf Nodları) taşıyan `physicalExamFindings` alanı eklendi — ayrı bir child entity/tablo yerine `jsonb` kolon seçildi (Hibernate 6'nın native `@JdbcTypeCode(SqlTypes.JSON)` desteğiyle), çünkü liste sabit ve her zaman bütün olarak güncelleniyor (vitals'a benzer, prescription item gibi değişken sayıda satır değil). `V25__encounter_physical_exam.sql` migration. Backend: `ExamBodySystem`/`ExamFindingStatus` enum'ları + `PhysicalExamFinding` record (domain), `UpdatePhysicalExamUseCase` (`RecordVitalsUseCase` ile birebir aynı iskelet), yeni `PUT /api/v1/encounters/{id}/physical-exam` (VET/ADMIN, aynı controller). Frontend: `PhysicalExamCard.tsx` — Vital Bulgular ile SOAP Notu kartları arasına, her sistem için 3'lü segmented toggle (Muayene Edilmedi/Normal/Anormal) + Anormal seçilince açılan not alanı. Objective serbest metin alanına hiç dokunulmadı — checklist ek/tamamlayıcı bir veri noktası, hekimin serbest metin yazma alışkanlığının yerini almıyor. Bu turda modülün ilk birim testleri de yazıldı (`EncounterTest`, `UpdatePhysicalExamUseCaseTest` — `coding-conventions.md`'deki `should_<sonuç>_when_<koşul>` formatı, Mockito ile port mock'lama), daha önce `encounter` modülünde hiç birim testi yoktu. `./mvnw test` (5/5 yeşil, `ApplicationModulesTest` dahil) + `tsc`/`vite build` temiz + ayrı bir portta (8081) gerçek Postgres'e karşı curl ile uçtan uca doğrulandı (PUT sonrası GET'te jsonb alanı doğru geri geliyor) — kullanıcının zaten çalışan 8080 örneğine dokunulmadı.

---

## Faz 3 — Vitrin (marketing site) ↔ Panel Uyum Denetimi ve Yol Haritası (2026-09-04, 2026-09-09'da güncellendi)

Bu tur, kullanıcının pazarlama sitesindeki (`vetly-site/index.html`, ayrı bir statik proje) her özellik iddiasının panelde gerçekten karşılığı olup olmadığını sorgulamasıyla başladı. Masaüstü köprüsü üzerinden bu repoya bağlanılıp `screen-priorities.md`/`implementation-plan.md` koda karşı satır satır tekrar okundu, ayrıca vitrin sitesinin tam metniyle karşılaştırıldı. Bulgular önem sırasına göre:

### 0. Kod değişikliği değil, önce iş/altyapı kararı gereken konular
- **Kalıcı production hosting:** `frontend/.env.production` hâlâ "test süreci" notuyla Render'a işaret ediyor, `backend/Dockerfile`'daki `-Xmx384m` sınırı da küçük bellekli (muhtemelen 512MB) bir Render instance'ına işaret ediyor.
- **e-Fatura gerçek entegratör hesabı:** GİB onaylı bir entegratörle (Foriba, Uyumsoft vb.) sözleşme + API anahtarı olmadan `EInvoiceGatewayPort`'un gerçek adaptörü yazılamaz.
- **SMS/WhatsApp gerçek sağlayıcı hesabı:** Twilio (veya Netgsm gibi yerli bir alternatif) hesabı + WhatsApp Business API onayı olmadan `NotificationSendPort`'un gerçek adaptörü etkinleşmiyor.
- **Klinik-içi fatura ödemeleri için gerçek ödeme sağlayıcısı:** `PaymentGatewayPort`/`IyzicoPaymentGatewayAdapter` şu an sadece Vetly'nin kendi SaaS abonelik/checkout akışı için var (`modules/platformadmin`). Hasta sahiplerinden tahsilat (`modules/billing`) hâlâ elle kaydediliyor.
- **Tanı Desteği (Diagnosis Assistant) için veri/model kararı:** CLAUDE.md'nin açık kuralı gereği gerçek bir tıbbi referans kaynağı olmadan tanı önerisi uydurulmayacak.

### 1. P0 — Vitrin bir şey vaat ediyor, panelde hiç karşılığı yok veya yanıltıcı
- [x] **Tanı Desteği — implemente edildi (2026-09-09):** Aşağıdaki "Ek not (2026-09-09, devam)" bölümüne bakınız. Vitrin sayfası artık bu konuda yanıltıcı değil.
- [x] **AI SOAP Generator — Ollama'dan Claude'a (Anthropic) geçildi, lokalde uçtan uca doğrulandı (2026-09-06 karar, 2026-09-09 çalışır hale getirildi):** Aşağıdaki "Ek not" bölümüne bakınız — üç ayrı sorun sırayla bulunup çözüldü.
- [ ] **e-Fatura mock modda:** Vitrin "e-Fatura entegrasyonuyla mevzuata uygun kesin" diyor, gerçekte GİB'e hiçbir şey gitmiyor (mock adaptör).

### 2. P1 — Gerçek ama eksik/yarım
- [ ] SMS/WhatsApp: tetikleme mantığı gerçek, gönderim mock.
- [ ] Stok & İlaç Yönetimi: `Medicine Management` stokla entegre bir katalog değil; ayrı bir `Inventory Dashboard` yok.
- [ ] Klinik-içi fatura ödemeleri için gerçek ödeme sağlayıcısı yok (elle kayıt).
- [ ] `Forgot Password` ekranı yok.
- [ ] `Subscription Plan Selection` ekranı yok.

### 3. Vitrin sayfası tarafında yapılacaklar
- **Bugün yapılabilir (kod değişikliği yok, sadece metin):** "Tanı Desteği" kartına "Yakında" rozeti eklemek veya kartı geçici kaldırmak; e-Fatura metnini gerçek duruma göre yumuşatmak.
- **Özellik tamamlandıkça:** her madde panelde gerçekten bitince vitrin metni tekrar gözden geçirilip güncellenmeli.

### Ek not (2026-09-09) — Anthropic geçişi lokalde doğrulandı, üç ayrı sorun çözüldü
Kullanıcı ile birlikte lokalde (`./mvnw spring-boot:run`) uçtan uca test edilirken sırasıyla üç farklı sorun bulunup çözüldü, ileride benzer bir entegrasyon yapılırken tekrar düşülmesin diye kayıt altına alınıyor. Not: bu turda ayrıca, önceki "Faz 3" denetim bölümünün bir önceki commit'te bilinmeyen bir sebeple (muhtemelen ayrı bir yerel oturumun eski bir kopya üzerinden çalışıp üzerine yazması) dosyadan silindiği fark edildi — bu bölüm o kaybolan içerikle birlikte yeniden oluşturuldu.

1. **`ai.provider` varsayılanı "ollama" kalmış:** Ayrı bir yerel oturumda `ai.provider` anahtarı eklenip Ollama/Claude adaptörleri (hem SOAP hem Tedavi Önerisi için) `@ConditionalOnProperty` ile ayrıştırılmış — mimari olarak iyi bir karar (adaptörü silip değiştirmek yerine ikisini de tutup anahtarla seçmek), ama varsayılan hâlâ `ollama` olduğu için `ANTHROPIC_API_KEY` girilse bile `AI_PROVIDER=claude` ayrıca set edilmeden sistem Ollama'ya (dolayısıyla mock'a) düşüyordu. **Çözüm:** hem lokalde hem production'da (Render) `AI_PROVIDER=claude` VE `ANTHROPIC_API_KEY` ortam değişkenlerinin ikisinin birden set edilmesi gerekiyor, sadece anahtar yeterli değil.
2. **401 Unauthorized:** Anahtar kopyalanırken/oluşturulurken bir sorun vardı (muhtemelen daha önce güvenlik amacıyla iptal edilen bir anahtarın kullanılmaya devam edilmesi) — kullanıcı yeni/doğru anahtarla düzeltti.
3. **JSON parse hatası → sonra `400 does not support assistant message prefill`:** `ClaudeSoapGenerationAdapter`'da JSON çıktısını garantilemek için "assistant prefill" (`"{"` ile ön doldurma) denendi, ama `claude-sonnet-5` modeli bu tekniği reddediyor. **Kalıcı çözüm:** prefill yerine Anthropic'in "tool use" (function calling) özelliği kullanıldı — model artık serbest metin değil, zorunlu bir `structure_soap_note` aracının parametresi olarak yapılandırılmış JSON üretiyor (`tool_choice: {type: "tool", name: "structure_soap_note"}`). Bu yöntem markdown'a sarılma riskini tamamen ortadan kaldırıyor ve prefill desteklemeyen modellerde de çalışıyor — ileride benzer bir JSON-çıktı ihtiyacı olursa (örn. Tanı Desteği) doğrudan bu desen kullanılmalı, prefill denenmemeli. `ClaudeTreatmentRecommendationAdapter`'ın JSON parse etmediği (serbest metin döndürdüğü) için bu sorunu hiç yaşamadığı ayrıca not edilsin.

Lokalde uçtan uca doğrulandı (SOAP Notu artık S/O/A/P alanlarını gerçekten ayrı ayrı dolduruyor, hatasız). **Kalan iş:** Render'da (production) hem `AI_PROVIDER=claude` hem `ANTHROPIC_API_KEY` ortam değişkenlerinin eklenmesi — henüz teyit edilmedi.

### Öncelik sırası önerisi
1. Render production değişkenleri (`AI_PROVIDER=claude`, `ANTHROPIC_API_KEY`) — AI SOAP Generator ve Tedavi Önerisi'ni canlıya taşımanın son adımı.
2. Vitrin metin düzeltmesi (Tanı Desteği + e-Fatura) — sıfır maliyetli, riski hemen azaltır.
3. e-Fatura / SMS gerçek sağlayıcı hesapları (ticari süreç, paralel yürütülebilir).
4. Tanı Desteği'nin gerçek implementasyonu (aynı Claude tool-use deseniyle, veri/model kararı netleşince).
5. P1 listesindeki kalan boşluklar (Forgot Password, Subscription Plan Selection, Inventory Dashboard, klinik-içi ödeme entegrasyonu).


### Ek not (2026-09-09, devam) — Tanı Desteği, Tedavi Önerisi mimarisiyle birebir aynı desende eklendi

Kullanıcı onayıyla ("onaylıyorum"), yeni bir tıbbi referans veritabanı kurmak yerine yukarıdaki Anthropic entegrasyonunda kanıtlanmış aynı port/adaptör mimarisi ve "AI önerisi, hekim onayı gerekir" deseni Tanı Desteği için de uygulandı:

- **Girdi genişletildi:** `EncounterClinicalContext` (encounter modülü, tek çağıran `GenerateTreatmentRecommendationUseCase` idi — düşük riskli genişletme) artık `subjective`/`objective` ham metinlerini ve `EncounterLookupAdapter`'da hesaplanan `vitalsSummary`/`physicalExamSummary` özetlerini de taşıyor (Assessment yerine bu alanlar tanı önerisi için girdi).
- **Yeni domain/application/infrastructure/api dosyaları** (`modules/ai` altında, Tedavi Önerisi'nin birebir aynı iskeleti): `DiagnosisSuggestionPort`/`Input`/`Draft`, `AiTaskType.DIAGNOSIS_SUGGESTION` (migration gerekmedi — `ai_jobs.task_type` CHECK constraint'siz `TEXT`), `ClinicalFindingsRequiredForDiagnosisException` (Subjective/Objective ikisi de boşsa 422 — `AssessmentRequiredForRecommendationException` ile aynı desen), `GenerateDiagnosisSuggestionUseCase`/`Command`/`Result`, `ClaudeDiagnosisSuggestionAdapter`/`OllamaDiagnosisSuggestionAdapter` (`ai.provider` anahtarıyla seçiliyor, ikisi de düz metin öneri döndürüyor — JSON parse gerekmediği için prefill/tool-use sorunu hiç yaşanmadı), `AiController`'a `POST /diagnosis-suggestions` + `.../decision` (kararlar için mevcut genel `RecordAiJobDecisionUseCase`/`DecideTreatmentRecommendationRequest` aynen yeniden kullanıldı, yeni DTO gerekmedi).
- **Frontend:** `aiApi.ts`'e `DiagnosisSuggestion` tipi + `generateDiagnosisSuggestion`/`decideDiagnosisSuggestion`; `EncounterPage.tsx`'te SOAP kartının hemen altına, "AI Tedavi Önerisi" kartından ÖNCE yeni bir "AI Tanı Desteği" kartı eklendi (Subjective/Objective dolu olunca aktifleşiyor, kabul edilince Assessment alanına — Plan'a değil — uygulanıyor). Ayarlar > AI Merkezi'ndeki eski birleşik "Tanı Desteği / Tedavi Önerisi — Planlanıyor" kartı da gerçek duruma göre ikiye ayrılıp "Aktif" olarak güncellendi.
- **Var olan bir test dosyasını da güncellemek gerekti:** `GenerateTreatmentRecommendationUseCaseTest`, `EncounterClinicalContext`'i doğrudan constructor ile kuruyordu — yeni alanlar eklenince iki test de güncellendi (dummy `subjective`/`objective`/`vitalsSummary`/`physicalExamSummary` değerleriyle).
- **Kapsam dışı bırakılan:** Ayrı bir "tanı seçim" UI'ı (dropdown/ICD kodu vb.) kurulmadı — Assessment serbest metin alanı olarak kalıyor, AI önerisi sadece bu alana metin olarak uygulanıyor (Tedavi Önerisi'nin Plan'a uygulanma şekliyle birebir aynı).

**Kalan iş:** Kullanıcı kendi makinesinde `./mvnw spring-boot:run` ile derleyip panelde test edecek (bu oturumun sandbox'ı Java 21/Maven'i çalıştıramıyor, sadece dosya düzenleyebiliyor). Sorunsuz çalışırsa, Render production ortam değişkenleri (`AI_PROVIDER=claude`, `ANTHROPIC_API_KEY`) hâlâ bekleniyor — bu iki özelliğin (SOAP + Tedavi Önerisi + şimdi Tanı Desteği) canlıda çalışması için ortak, tek kalan adım.


### Ek not (2026-09-09, devam #2) — "content[0] her zaman metin bloğudur" varsayımı yanlış çıktı (extended thinking bug'i)

Kullanıcı Tanı Desteği'ni test ederken (başarılı) ardından Tedavi Önerisi'ni tekrar denedi, bu sefer `modelConnected: true` ama öneri metni tamamen boş ("—") döndü — hata da loglanmadı (try/catch'e hiç düşmedi). Geçici bir teşhis logu eklenip ham Anthropic cevabı görüldü: `claude-sonnet-5` bu istekte **extended thinking** ile cevap vermiş, `content[]` dizisinin **0. elemanı `type: "thinking"`** (metin yerine muhakeme, `"text"` alanı yok), asıl `type: "text"` bloğu ise **1. elemanda**. `ClaudeTreatmentRecommendationAdapter`/`ClaudeDiagnosisSuggestionAdapter` ikisi de `content.path(0).path("text")` ile sabit index varsayıyordu — thinking bloğu geldiğinde `text` alanı olmadığından sessizce boş string dönüyordu.

**Kalıcı çözüm:** İkisine de `extractText(JsonNode root)` helper'ı eklendi — `content[]` içinde index varsaymadan `type == "text"` olan ilk bloğu arıyor, thinking/başka blok tiplerini atlıyor. `ClaudeSoapGenerationAdapter` zaten bu bug'dan etkilenmiyordu çünkü tool-use deseninde zaten `type == "tool_use"` olan bloğu arayarak buluyor (index varsaymıyor) — aynı sağlam desen artık üç adaptörde de var.

**Ders:** Anthropic Messages API'de `content[]` dizisinin sırası/uzunluğu sabit değil (extended thinking, gelecekte başka blok tipleri) — hangi adaptör olursa olsun content bloklarını HER ZAMAN `type` alanına göre filtrelemek gerekiyor, index'e güvenilmemeli.

Kullanıcı düzeltmeden sonra Tedavi Önerisi'ni tekrar test etti, sorunsuz çalıştı — **SOAP + Tedavi Önerisi + Tanı Desteği üçü de lokalde uçtan uca doğrulandı, hatasız.**

### Ek not (2026-09-09, devam #3) — git push tamamlandı

Tüm değişiklikler (Tanı Desteği + extended-thinking bug düzeltmesi + yeni test coverage) `main`'e push edildi (commit `ec34e4f`). `./mvnw test` ve `npm run build` push öncesi kullanıcı tarafından yeşil doğrulandı. Render'ın otomatik deploy'u tetiklenmiş olmalı. **Kalan tek adım:** Render Environment'a `AI_PROVIDER=claude` + `ANTHROPIC_API_KEY` eklenmiş mi teyit edilmesi, ardından production'da üç AI özelliğinin de (SOAP, Tedavi Önerisi, Tanı Desteği) canlıda test edilmesi.


## e-Fatura: faturaentegrator.com gercek entegrasyonu (2026-09-09)

Mock adaptorun yanina gercek bir saglayici (faturaentegrator.com, Dummy Fatura
test hesabi -- fiyat/ucretsiz test onceligiyle secildi) eklendi. Ozet:

- **Domain genisletildi:** `EInvoiceSubmissionRequest`'e satir kalemleri
  (`EInvoiceLineItem`, billing modulundeki `InvoiceLine`'dan turetiliyor) ve
  tam alici bilgisi (sehir/ilce) eklendi.
- **TCKN karari:** Sahiplerin gercek TCKN'si sistemde hic tutulmuyor (sadece
  maskeli hali var) -- kullanici ile birlikte GIB'in "isimsiz/nihai tuketici"
  TCKN'si (11111111111) kullanilmasina karar verildi; her fatura otomatik
  e-Arsiv olarak kesiliyor. Gercek TCKN toplama (KVKK etkili, kayit akisini
  degistiren bir is) ileride ayrica degerlendirilebilir.
- **Asenkron akis karari:** faturaentegrator `POST /invoices` ANINDA GIB
  ETTN'i donmuyor (workflow_status=processing). Yeni bir ara durum
  (`EInvoiceSubmissionStatus.PROCESSING`) eklendi. Kullanici ile birlikte
  callback (webhook) yaklasimi secildi (polling yerine) -- callback govdesi
  sadece `{invoice_id, team_id, time, hash}` tasiyor (durum bilgisi YOK), bu
  yuzden bildirim gelince ayrica `GET /invoices/{id}` ile guncel durum
  cekiliyor (`EInvoiceGatewayPort.fetchStatus`). Imza HMAC-SHA512 ile
  dogrulaniyor (`FaturaEntegratorCallbackController`, IyzicoPaymentGatewayAdapter
  ile ayni HMAC deseni).
- **"Tekrar Dene" guvenligi:** PROCESSING durumdaki bir gonderim tekrar
  denenirse saglayici tarafinda mukerrer fatura olusur -- bu yuzden
  `RetryEInvoiceSubmissionUseCase` artik PROCESSING icin
  `EInvoiceSubmissionAlreadyProcessingException` firlatiyor.
- **Musteri eslestirme:** faturaentegrator `customer.id` alanini sayisal
  bekliyor, sahip kayitlarimiz UUID -- sahip UUID'sinden sabit bir sayisal ID
  turetiliyor (ayni sahip = ayni ID = saglayici panelinde mukerrer musteri
  olusmuyor).
- **Bilinen sinirlama:** KDV orani 0 olan satirlar icin GIB istisna
  kodu/gerekcesi modellenmiyor (InvoiceLine'da bu alan yok) -- boyle bir
  satir gelirse istek saglayici tarafinda reddedilir (yanlis istisna kodu
  uydurmak yerine bilincli tercih).
- **Config:** `EFATURA_PROVIDER=faturaentegrator` (varsayilan `mock`),
  `EFATURA_FATURAENTEGRATOR_API_KEY`, `EFATURA_FATURAENTEGRATOR_INVOICE_INTEGRATION_ID`
  (test hesabi: 753), `EFATURA_FATURAENTEGRATOR_SALE_CHANNEL_ID` (test kanali:
  1045), `EFATURA_FATURAENTEGRATOR_CALLBACK_BASE_URL` (public erisilebilir
  backend adresi -- localde callback gercekten test edilemez, Render'da
  https://<servis>.onrender.com olarak set edilmeli).
- **Yeni migration:** `V32__efatura_provider_reference.sql`
  (`efatura_submission.provider_reference` kolonu).
- **Testler yazildi:** `FaturaEntegratorEInvoiceGatewayAdapterTest`,
  `FaturaEntegratorCallbackControllerTest`, `ApplyEInvoiceCallbackUseCaseTest`,
  `RetryEInvoiceSubmissionUseCaseTest`. Bu oturumun sandbox'i Maven/Java
  calistiramadigi icin `./mvnw test` kullanicinin kendi makinesinde
  DOGRULANMADI -- bir sonraki adim budur.
- **Kalan is:** kullanici `./mvnw test` calistirip sonucu paylasacak; localde
  Dummy Fatura ile gercek bir `POST /invoices` denemesi (env degiskenleri set
  edilip backend ayaga kaldirilarak) yapilacak; callback ucu ancak public bir
  URL'den (Render deploy'u ya da ngrok gibi bir tunel) gercekten test
  edilebilir.

### Guncelleme (2026-09-09, ayni gun) -- ilk gercek lokal test ve soyad bug'i

Kullanici `EFATURA_PROVIDER=faturaentegrator` ile lokalde gercek bir test
yaptı: `./mvnw test` yesil gecti, backend gercek adaptoru sececek sekilde
ayaga kalkti, `POST /invoices` faturaentegrator'a basariyla ulasti
(providerReference=232420 dogru sekilde kaydedildi, durum PROCESSING /
"GİB Resmileştiriyor" -- callback localhost'a ulasamadigi icin bu durumda
takili kalmasi beklenen ve dogru davranis).

faturaentegrator panelinde ayni fatura icin "Resmileştirme Hatalı" bulundu:
**"Soyad(FamilyName) alanı 2 haneden az olamaz."** Kok sebep: test sahibi
"hale" sistemde tek kelimelik isim olarak kayitli (Owner'da ayri
firstName/lastName yok, tek serbest metin `fullName` alani var), adaptorun
`splitName()` fonksiyonu boşluk bulamayinca soyadi `"-"` (1 karakter) ile
dolduruyordu, GIB bunu reddediyor.

Kullaniciya soruldu, "gercek musterilerin zaten soyadi olur, soyadsiz islem
yapilmamali" karari verildi -- yani soyad UYDURULMUYOR, bunun yerine:

- `EInvoiceSubmissionExecutor.attemptSubmit()` artik saglayiciyi hic
  cagirmadan once `owner.fullName()`'de gecerli bir soyad (son bosluktan
  sonraki kisim >= 2 karakter) olup olmadigini kontrol ediyor. Yoksa
  submission direkt FAILED'e geciyor, saglayici tarafinda bos/hatali kayit
  birikmiyor.
- **Yeni: `failure_reason` kolonu** (`V33__efatura_submission_failure_reason.sql`)
  -- daha once basarisizlik SEBEBI hicbir yerde saklanmiyordu (`markFailed()`
  parametresizdi), personel neden basarisiz oldugunu gormek icin
  faturaentegrator panelini acmak zorundaydi. Artik hem on-dogrulama
  mesaji hem de saglayicinin/GIB'in gercek hata metni
  (`EInvoiceSubmissionOutcome.message()`) `EInvoiceSubmission.failureReason`'a
  yaziliyor, API (`EInvoiceSubmissionResponse.failureReason`) ve frontend
  (Ayarlar > e-Fatura tablosunda FAILED satirlarin altinda kucuk kirmizi
  metin) uzerinden gorunuyor.
- Yeni test: `EInvoiceSubmissionExecutorTest` (bu sinifin daha once hic testi
  yoktu) -- soyad on-dogrulamasi, basarili gonderim, saglayici reddi,
  submission bulunamama senaryolarini kapsiyor.
- `./mvnw test` bu degisiklikler icin de kullanicinin kendi makinesinde
  DOGRULANMADI -- bir sonraki adim budur.

**Kalan is:** "hale" test sahibinin kaydina soyad eklenip (or. "hale
Yilmaz") ayni fatura "Tekrar Dene" ile yeniden denenip artik SUBMITTED/
Resmilesiyor durumuna gecebildigi dogrulanacak; sonra callback ucu Render
deploy'u ya da ngrok ile test edilecek.

### Guncelleme (2026-09-09, ayni gun) -- asil kok sebep: Ad Soyad hic duzenlenemiyordu

Kullanici "hale" sahibinin soyadini duzeltmeye calisti ama Ayarlar > e-Fatura
yine ayni hatayi verdi. Sebebi bulundu: **`fullName` (Ad Soyad) alani
kayittan sonra HICBIR yerden guncellenemiyordu** -- ne `Owner` domain
sinifinda bir metot, ne `UpdateOwnerCommand`/`UpdateOwnerRequest`'te bir
alan, ne de `OwnerEditModal.tsx`'te bir form alani vardi. Kullanicinin
duzenle ekraninda gordugu tek isimle-ilgili alan "İkinci ad (opsiyonel)"
(middleName) idi -- kullanici oraya "Say" yazinca sahip detay basligi
kozmetik olarak "hale Say" gorunuyordu ama gercek `fullName` kolonu hala
sadece "hale" olarak kaliyordu, bu yuzden e-Fatura'nin soyad kontrolu
(fullName'e bakiyor, middleName'e degil) tekrar basarisiz oluyordu.

Bu, e-Fatura ozelligiyle ortaya cikan ama daha once de var olan genel bir
urun eksigiydi -- duzeltildi:

- `Owner.updateFullName(String)` (domain) eklendi.
- `UpdateOwnerCommand`, `UpdateOwnerRequest` (`@NotBlank fullName`),
  `OwnersController`, `UpdateOwnerUseCase` zincirine `fullName` eklendi.
- `OwnerEditModal.tsx`'e "Ad Soyad" (zorunlu) alani eklendi;
  `UpdateOwnerPayload`'a `fullName: string` eklendi.
- Yeni test: `UpdateOwnerUseCaseTest` (bu use case'in hic testi yoktu).

**Kalan is:** kullanici `./mvnw test` calistirip yeşil oldugunu dogrulayacak
(bu degisiklikler icin henuz DOGRULANMADI); sonra "hale" kaydini gercekten
"Ad Soyad" alanindan "hale Yılmaz" gibi duzenleyip Ayarlar > e-Fatura'dan
"Tekrar Dene" ile test edecek.
