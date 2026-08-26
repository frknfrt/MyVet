# Vetly — Yapay Zeka Destekli Veteriner Klinik Yönetim Platformu
## Gereksinim ve Tasarım Dokümanı (v0.1 — Taslak)

> Bu doküman, Kolayvet/BulutVet gibi yerel oyuncuların operasyonel derinliğini, Shepherd'ın workflow-first UX'ini, Digitail'in AI-native yapısını ve global pazardaki diğer PIMS (Practice Information Management System) ürünlerinin öne çıkan özelliklerini tek çatı altında toplayan, Spring Boot + React ile geliştirilecek bir SaaS ürünü için başlangıç noktasıdır. Birlikte iteratif olarak genişleteceğiz.

---

## 1. Vizyon ve Amaç

**Vizyon:** Türkiye pazarına özel (TARBİL, e-Fatura, KVKK) ama global UX ve AI standartlarında bir veteriner klinik işletim sistemi kurmak — kliniklerin idari yükünü azaltıp klinik zamanını artıran, hayvan sahiplarını uygulama içinde tutan, veriyi (medikal + operasyonel) AI ile aktif olarak kullanan bir platform.

**Neden şimdi / neden farklı:**
- Türkiye'deki oyuncular (Kolayvet, BulutVet, MVM, Micro Medik) operasyonel olarak güçlü ve TARBİL entegrasyonuna sahip, ama UX ve AI olgunluğu düşük.
- Global oyuncular (Shepherd, Digitail, Covetrus Pulse, ezyVet, Provet Cloud) UX ve AI'da güçlü ama Türkiye'ye özgü mevzuat/entegrasyon (TARBİL, e-Fatura/e-Arşiv, KVKK) sunmuyor.
- **Boşluk = Türkiye mevzuat uyumu + global-seviye UX + gerçek AI iş akışları.**

---

## 2. Rakip Analizi (Özet)

| Ürün | Güçlü Yönler | Zayıf/Eksik Yönler |
|---|---|---|
| **Kolayvet** (TR) | TARBİL entegrasyonu, geniş özellik seti, çift mobil uygulama (hekim + sahip), şeffaf fiyatlandırma, büyükbaş/suni tohumlama desteği | UX eski/karmaşık, AI yok denecek kadar az, modern telemedicine/AI-scribe yok |
| **BulutVet** (TR) | Basit, uygun fiyatlı, bulut tabanlı, toplu SMS | Özellik derinliği sınırlı, AI yok, kurumsal/çok şubeli kliniklere zayıf |
| **Shepherd** (US) | Workflow-first tasarım, SOAP kaydına bağlı otomatik faturalama, whiteboard/task board, TranscribeAI (canlı muayene → SOAP), DiagnoseAI (tanı desteği), barkodlu envanter, text-to-pay | Türkiye mevzuatı yok, İngilizce-merkezli, büyük/çok şubeli hastanelerde sınırlı |
| **Digitail** | AI-native (15+ AI workflow), Tails AI (SOAP dictation, sesli not, otomatik intake), güçlü pet-parent app + telemedicine (sanal lobi), 30+ entegrasyon (IDEXX, Antech, Zoetis), akıllı charge capture | Destek yanıt süreleri değişken, envanter/reçete yönetimi zayıf, fiyat şeffaf değil |
| **Covetrus Pulse** | Uçtan uca workflow otomasyonu, AI Treatment Board (ekip bazlı hasta takibi) | Ekosistem kilitlenmesi riski, karmaşıklık |
| **ezyVet / Provet Cloud** | Kurumsal/çok şubeli hastane yönetimi, laboratuvar/görüntüleme entegrasyonları | UX ağır, küçük kliniklere aşırı kapsamlı |

**Sonuç — Farklılaştırıcı konumlandırma:** *"Shepherd'ın sadeliği + Digitail'in AI'ı + Kolayvet'in TARBİL/yerel mevzuat uyumu, tek platformda."*

---

## 3. Kullanıcı Personaları

1. **Klinik Sahibi / Baş Hekim** — çoklu şube görmek ister, ciro/verimlilik raporları, personel performansı.
2. **Muayene Hekimi** — hızlı SOAP kaydı, AI ile ses/otomatik not, geçmiş hasta verisine hızlı erişim, reçete yazımı.
3. **Resepsiyonist / Klinik Asistanı** — randevu, check-in/check-out, ödeme tahsilatı, hatırlatma gönderimi.
4. **Teknisyen / Yardımcı Sağlık Personeli** — tedavi görevleri (whiteboard/task board), yatış takibi, aşı uygulama.
5. **Hayvan Sahibi (Pet Parent App kullanıcısı)** — randevu alma, aşı takvimi, reçete/tahlil sonucu görüntüleme, ödeme, mesajlaşma, AI destekli ön-triyaj sohbeti.
6. **Muhasebe / İşletme Yöneticisi** — e-Fatura/e-Arşiv, stok/tedarikçi ödemeleri, kâr-zarar raporları.
7. **Sistem Yöneticisi (Süper Admin - SaaS tarafı)** — tenant yönetimi, plan/lisans, denetim logları.

---

## 4. Fonksiyonel Gereksinimler (Modül Bazlı)

### 4.1 Randevu & Takvim Yönetimi
- Çoklu hekim/şube takvimi, sürükle-bırak randevu, online randevu (widget + pet parent app).
- Hizmet tipine göre süre/oda/ekipman ataması, tekrarlayan randevular (aşı serisi, kontrol).
- No-show tahmini (AI) ve otomatik hatırlatma (SMS/WhatsApp/push, çok kanallı).
- Bekleme listesi ve boş slot doldurma otomasyonu.

### 4.2 Hasta & Sahip Yönetimi (CRM + EMR)
- Sahip profili (KVKK'ya uygun onay kayıtları dahil), birden fazla hayvan, birden fazla sahip/hane bağlama.
- Tür/ırk/yaş/mikroçip/TARBİL kimlik no bazlı hasta kartı.
- SOAP tabanlı elektronik muayene kaydı (Subjective/Objective/Assessment/Plan), şablonlanabilir muayene formları (tür/branşa göre: küçükbaş, büyükbaş, egzotik, kedi-köpek).
- Zaman çizelgesi görünümü: tüm geçmiş muayene, aşı, tahlil, görüntüleme, reçete tek ekranda (Digitail'in "connected records" yaklaşımı).
- Ameliyat/anestezi kayıtları, öncesi-sonrası checklist.

### 4.3 Aşı & Koruyucu Sağlık Takibi
- Türe özel aşı protokolleri, otomatik hatırlatma (sahibe + hekime), sertifika/aşı karnesi PDF üretimi.
- Parazit önleme (iç-dış parazit) takvimi.

### 4.4 Reçete & İlaç Yönetimi
- Elektronik reçete, ilaç etkileşim/kontrendikasyon uyarıları (AI destekli), online eczane entegrasyonu (Shepherd/Digitail benzeri).
- Kontrollü ilaç (narkotik) dijital kayıt defteri (e-CDR benzeri) — Türkiye mevzuatına uyarlanacak.

### 4.5 Laboratuvar & Görüntüleme Entegrasyonu
- Harici lab cihaz/analizör entegrasyonu (HL7/API), sonuçların otomatik hasta kartına işlenmesi.
- Röntgen/ultrason/DICOM görüntü depolama ve görüntüleme; radyoloji yorum iş akışı.
- AI destekli görüntü ön-analizi (anomali işaretleme — hekim onayına tabi, tanı koymaz).

### 4.6 Yatış (Boarding) / Klinik İçi Takip
- Kafes/oda bazlı yatış planı, whiteboard/task board (Shepherd tarzı): bekleyen-devam eden-tamamlanan tedavi görevleri.
- Vital bulgu (nabız, ateş, solunum) zaman çizelgesi grafiği.

### 4.7 Faturalama, Ön Muhasebe & Ödeme
- Tedavi/işlem bazlı otomatik charge capture (yapılan işlem → fatura kalemi, Shepherd/Digitail'in temel farkı).
- e-Fatura / e-Arşiv entegrasyonu (GİB), KDV, ödeme planları/taksit.
- Online ödeme (kart, temassız, "text-to-pay" linki), sahada mobil POS desteği.
- **Kasa yönetimi:** Günlük kasa açma/kapama, nakit mutabakatı, vardiya bazlı kasa raporu — rakip analizinde tespit edilen ve MVP'ye eklenen kritik bir özellik, klinik sahibinin günlük nakit akışını kontrol etmesi için olmazsa olmaz.
- **Borç listesi / cari hesap takibi:** Sahip bazlı açık bakiye, gecikmiş ödeme takibi, toplu hatırlatma gönderimi. Sadece fatura bazlı değil, sahip bazlı konsolide görünüm.
- Sigorta/pet insurance claim desteği (Türkiye'de yeni gelişen bir alan — ileri faz).

### 4.8 Stok & Envanter
- Barkod/QR ile stok takibi, otomatik düşüm (kullanılan malzeme → stoktan otomatik azalma), son kullanma tarihi/lot takibi.
- Tedarikçi sipariş otomasyonu, minimum stok uyarısı, AI destekli talep tahmini.

### 4.9 TARBİL & Mevzuat Entegrasyonu (Türkiye'ye özgü — kritik farklılaştırıcı)
- T.C. Tarım ve Orman Bakanlığı TARBİL sistemine hasta/aşı/reçete bildirimi.
- Büyükbaş/küçükbaş hayvan kimliklendirme, suni tohumlama kayıtları.
- Resmi denetimlerde güncel stok/ilaç raporu üretimi.

### 4.10 İletişim & Pazarlama
- Çok kanallı otomasyon: SMS, WhatsApp Business API, push, e-posta.
- Kampanya/sadakat programı, doğum günü/aşı hatırlatmaları, geri kazanım (win-back) akışları.
- İki yönlü mesajlaşma (klinik ↔ sahip) uygulama içinde.

### 4.11 Pet Owner Mobil / Web Uygulaması
- Randevu alma, aşı takvimi, tahlil/reçete görüntüleme, ödeme, dosya paylaşımı (Digitail'in cloud-link yaklaşımı — büyük dosya e-posta yerine link).
- AI destekli ön-triyaj sohbet asistanı ("acil mi, randevu mu?").
- Telemedicine (video konsültasyon, sanal bekleme odası — Digitail'de fark yaratan özellik).

### 4.12 Raporlama & Analitik
- Klinik/şube bazlı ciro, doluluk, hekim performansı, ilaç/stok maliyeti dashboard'ları.
- **Çok boyutlu gelişmiş raporlar:** Her modül için ayrı detaylı rapor seti (hasta bazlı, ürün bazlı, hekim bazlı, kampanya bazlı) — sadece üst seviye KPI kartları değil, filtrelenebilir/dışa aktarılabilir (Excel/PDF) rapor tabloları. Rakip analizinde tespit edilen ve MVP'ye eklenen bir eksiklik.
- Çok şubeli işletmeler için konsolide yönetici paneli.

### 4.13 Website & Online Varlık (MVP'ye eklendi)
- Klinik için otomatik oluşturulan basit bir tanıtım web sitesi (klinik bilgileri, hizmetler, konum).
- Web sitesine gömülü **online randevu widget'ı** — sahip, klinik sitesinden doğrudan randevu talep edebilir, talep otomatik olarak "Online Booking" kaynaklı randevu olarak sisteme düşer.
- Bu, yeni müşteri kazanımı (SEO/organik trafik) açısından rakiplerin sunduğu ama bizim ilk planımızda atladığımız kritik bir özellik.

### 4.14 Diğer Tamamlayıcı Özellikler
- **Çıktı şablonları:** Reçete, fatura, aşı sertifikası gibi belgeler için özelleştirilebilir yazdırma şablonları.
- **Barkodlu satış (retail POS):** Mama, aksesuar gibi retail ürünler için hızlı barkod okutmalı satış akışı — muayene/reçete akışından ayrı, kasa ile entegre.
- **Hizmet kataloğu yönetimi:** `SERVICE_TYPE` veri modelinin karşılığı olan, fiyat listesini yönetme ekranı (şu ana kadar veri modelinde vardı, yönetim ekranı planlanmamıştı).
- **Klinik geneli aşı görünümü:** Sadece hasta bazlı değil, "bu hafta/ay kimin aşısı var" şeklinde klinik çapında konsolide bir aşı takvimi.
- **Dış müşteriler / potansiyel kayıtlar (lead):** Henüz tam hasta kaydına dönüşmemiş, ilk temas kurulmuş potansiyel müşteri kayıtları — pazarlama/satış hunisinin ilk adımı.
- **Depo yönetimi:** Şube stoğundan ayrı, merkezi/ana depo kavramı — çok şubeli zincirlerde şubeler arası transfer öncesi ana stok noktası.
- **Vet-zon tarzı B2B pazaryeri (Faz 3 fikri):** Tedarikçilerin platform içinden doğrudan ürün satabildiği, platformun komisyon aldığı bir pazaryeri — ek bir gelir modeli fikri, MVP kapsamı dışında ama stratejik olarak not edildi.

### 4.15 Rol & Kullanıcı Yönetimi
- Şube/rol bazlı yetkilendirme (RBAC), aktivite log (denetim izi), KVKK erişim kayıtları.

---

## 5. Yapay Zeka Katmanı (Ayrıntılı)

| AI Özelliği | Referans Ürün | Açıklama |
|---|---|---|
| **AI Scribe / Sesli SOAP** | Shepherd TranscribeAI, Digitail Tails AI | Muayene sesini dinler, SOAP alanlarını otomatik doldurur, hekim onaylar/düzenler. |
| **AI Tanı Desteği** | Shepherd DiagnoseAI | Hasta geçmişi + semptomlara göre olası tanı/ayırıcı tanı önerisi (klinik karar destek, kesin tanı değil). |
| **AI Görüntü Ön-Analizi** | (pazar boşluğu) | Röntgen/dermatoloji fotoğraflarında anomali işaretleme. |
| **Akıllı Charge Capture** | Digitail | Yapılan işlemleri otomatik fatura kalemine çevirme, kaçan geliri azaltma. |
| **No-Show Tahmini & Otomatik Doldurma** | Covetrus, pazar boşluğu | Randevuya gelmeme riskini tahmin edip proaktif hatırlatma/liste doldurma. |
| **AI Ön-Triyaj Sohbet Botu (Sahip Uygulaması)** | Pazar boşluğu / kısmen mevcut | Sahibin şikayetini alır, aciliyet seviyesi önerir, randevu tipini önceliklendirir. |
| **AI Müşteri İletişim Taslağı** | NectarVet | Takip mesajı, taburcu talimatı, aşı hatırlatma metnini otomatik taslaklama. |
| **AI Stok Talep Tahmini** | Pazar boşluğu | Geçmiş kullanım verisine göre sipariş önerisi. |
| **AI Çağrı Özeti** | NectarVet | Gelen telefon görüşmelerinin otomatik özetlenmesi ve kayda işlenmesi. |

**Mimari not:** AI özellikleri, tek bir sağlayıcıya (örn. yalnızca OpenAI) bağımlı olmayacak şekilde bir **"AI Gateway" soyutlama katmanı** üzerinden çağrılmalı (model sağlayıcısı değiştirilebilir, maliyet/performansa göre yönlendirme yapılabilir). Klinik/tanı önerileri her zaman **"hekim onayı gerektirir"** ilkesiyle sunulmalı — nihai karar hekimde kalmalı (sorumluluk ve mevzuat açısından kritik).

---

## 6. Fonksiyonel Olmayan Gereksinimler

- **KVKK Uyumu:** Açık rıza yönetimi, veri saklama/silme politikaları, veri işleme envanteri, sahip verisinin klinik değişiminde taşınabilirliği.
- **Veri Güvenliği:** Şifreleme (at-rest & in-transit), rol bazlı erişim, 2FA (özellikle klinik sahibi/admin için).
- **Performans:** Randevu/hasta arama < 300ms, AI SOAP üretimi < 5-10 sn (asenkron, arka planda).
- **Ölçeklenebilirlik:** Çok kiracılı (multi-tenant) mimari, tek klinikten zincir kliniklere kadar ölçekleme.
- **Kullanılabilirlik:** Offline-first mobil senaryolar (zayıf internet olan kırsal/büyükbaş sahada çalışma ihtimali — Kolayvet'in büyükbaş/suni tohumlama kullanım senaryosu dikkate alınmalı).
- **Yedekleme & Felaket Kurtarma:** Günlük otomatik yedek, RPO/RTO hedefleri.
- **Denetlenebilirlik:** Resmi denetimlerde (Bakanlık) ilaç/stok raporu anında üretilebilmeli.

---

## 7. Teknik Mimari (Öneri — Tartışmaya Açık)

### 7.1 Genel Yaklaşım
- **Başlangıç:** *Modüler monolit* (Spring Boot, domain bazlı paketleme: `appointment`, `patient`, `billing`, `inventory`, `ai`, `notification` modülleri) — MVP hızını artırır.
- **İleri faz:** Yük/ölçek gerektiren modüller (AI işleme, bildirim gönderimi) ayrı servislere ayrılabilir (event-driven, Kafka/RabbitMQ ile).

### 7.2 Backend (Spring Boot)
- Spring Boot 3.x, Spring Security (JWT + rol bazlı yetkilendirme), Spring Data JPA.
- PostgreSQL (ana veritabanı — multi-tenant: şema-bazlı veya tenant_id kolonlu tek şema, klinik sayısına göre karar verilecek).
- Redis (cache, oturum, rate-limit).
- Asenkron işler için mesaj kuyruğu (RabbitMQ/Kafka) — AI SOAP üretimi, bildirim gönderimi, TARBİL senkronizasyonu gibi uzun süren işler için.
- Nesne depolama (S3 uyumlu — MinIO/AWS S3) — DICOM görüntü, dosya, ses kaydı.
- AI Gateway servisi: Anthropic/OpenAI API çağrılarını soyutlayan ayrı modül/servis.

### 7.3 Frontend (React)
- React + TypeScript, durum yönetimi (Redux Toolkit veya Zustand/React Query kombinasyonu — sunucu state'i için React Query önerilir).
- Component kütüphanesi: kurumsal, temiz bir tasarım sistemi (Shepherd/Digitail seviyesinde sade UX hedefi).
- Randevu takvimi için sürükle-bırak (örn. FullCalendar), whiteboard/task board için özel kanban bileşeni.

### 7.4 Mobil (Pet Owner App)
- React Native (kod paylaşımı avantajı) veya ayrı native — ürün stratejisine göre karar.

### 7.5 Entegrasyonlar
- TARBİL API (Bakanlık), GİB e-Fatura/e-Arşiv, WhatsApp Business API, SMS sağlayıcı, ödeme altyapısı (iyzico/PayTR gibi yerel PSP), lab cihaz API'leri (HL7/FHIR uyarlaması).

---

## 8. Üst Seviye Veri Modeli (Taslak Varlıklar)

`Tenant/Clinic` → `Branch` → `User(Role)` ; `Owner` → `Patient(Animal)` → `Visit/Encounter(SOAP)` → `Prescription`, `LabResult`, `ImagingRecord`, `VaccinationRecord` ; `Appointment` ; `Invoice` → `InvoiceLine` ; `InventoryItem` → `StockMovement` ; `Notification` ; `AIJob` (async AI iş kuyruğu takibi).

*(Detaylı ER diyagramını bir sonraki adımda birlikte çıkarabiliriz.)*

---

## 9. Faz Planı / Yol Haritası (Öneri)

**Faz 1 — MVP (temel klinik operasyonu):**
Randevu, hasta/sahip yönetimi, SOAP kaydı, temel faturalama, **kasa yönetimi, borç listesi/cari hesap**, aşı takibi, basit stok, SMS bildirim, TARBİL entegrasyonu (Türkiye'de olmazsa olmaz), **klinik web sitesi + online randevu widget'ı** (rakip analiziyle MVP'ye yükseltildi).

**Faz 2 — Farklılaşma:**
Pet Owner mobil app, AI Scribe (sesli SOAP), online ödeme, whiteboard/task board, e-Fatura tam entegrasyonu, WhatsApp entegrasyonu, gelişmiş çok boyutlu raporlama, barkodlu satış, çıktı şablonları.

**Faz 3 — İleri AI & Ölçek:**
AI tanı desteği, AI görüntü ön-analizi, telemedicine, no-show tahmini, çok şubeli konsolide raporlama, sigorta claim desteği, **Vet-zon tarzı B2B tedarikçi pazaryeri**, depo yönetimi.

---

## 10. Sonraki Adımlar

Bu doküman bir başlangıç taslağıdır. Birlikte derinleştirebileceğimiz alanlar:
1. Detaylı **ER diyagramı** ve veritabanı şeması.
2. **Spring Boot modül/paket mimarisi** (klasör yapısı, katmanlama) taslağı.
3. **Faz 1 (MVP) için kullanıcı hikayeleri** ve sprint planı.
4. **AI Gateway** için teknik tasarım (hangi model, hangi görev, maliyet stratejisi).

Hangisiyle devam etmek istersin?
