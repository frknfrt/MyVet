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
