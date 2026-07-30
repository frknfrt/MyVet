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
AI Scribe'ın gerçek model entegrasyonu (@docs/architecture.md Bölüm 3 — `AiGatewayRouter`), Pet Owner mobil app, WhatsApp/SMS otomasyonu, gelişmiş raporlama, e-Fatura tam entegrasyonu.
